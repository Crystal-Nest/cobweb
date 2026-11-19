package it.crystalnest.cobweb.api.pack.generation;

import it.crystalnest.cobweb.Constants;
import it.crystalnest.cobweb.api.pack.generation.PackGeneration.Task;
import it.crystalnest.cobweb.api.pack.generation.input.CapturedInput;
import it.crystalnest.cobweb.api.pack.generation.lifecycle.LoadKind;
import it.crystalnest.cobweb.api.pack.generation.lifecycle.PackLoadOutcome;
import it.crystalnest.cobweb.api.pack.generation.lifecycle.Phase;
import it.crystalnest.cobweb.api.pack.generation.lifecycle.Stage;
import it.crystalnest.cobweb.api.pack.query.PackQueryLifecycle;
import it.crystalnest.cobweb.platform.Services;
import it.crystalnest.cobweb.platform.services.PackHelper;
import it.crystalnest.cobweb.platform.services.PackHelper.RegisteredPack;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.util.Util;
import net.minecraft.world.flag.FeatureFlagSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Coordinates generated pack content with the native resource-loading boundaries.
 */
@ApiStatus.Internal
public final class DynamicPackLoads {
  /**
   * World directories associated with native repositories without retaining those repositories.
   */
  private static final Map<PackRepository, Path> WORLD_ROOTS = Collections.synchronizedMap(new WeakHashMap<>());

  /**
   * Whether initialization has ended and generation declarations are frozen.
   */
  private static final AtomicBoolean declarationsClosed = new AtomicBoolean();

  private DynamicPackLoads() {}

  /**
   * Creates the native resource supplier for a registered pack.
   *
   * @param definition registered pack definition.
   * @return supplier for bundled and generated resources.
   */
  public static Pack.ResourcesSupplier resources(RegisteredPack definition) {
    return new GeneratedPackResources.Supplier(definition);
  }

  /**
   * Opens file-rule layers and determines their namespaces before native resource indexing.
   *
   * @param type native pack side.
   * @param packs selected openings whose ownership transfers to the result.
   * @param kind current load kind.
   * @param fullLoadFiles retained world output for a server reload, if applicable.
   * @param log trace shared with the native load.
   * @return openings in unchanged native priority order.
   * @throws IllegalArgumentException if a selected file rule manifest is invalid.
   */
  public static List<PackResources> prepare(PackType type, List<PackResources> packs, LoadKind kind, @Nullable FullLoadFiles fullLoadFiles, PackLoadLog log) {
    var prepared = new ArrayList<>(packs);
    log.select(packs);
    try {
      for (int index = 0; index < prepared.size(); index++) {
        var pack = prepared.get(index);
        if (pack instanceof GeneratedPackResources generated) {
          log.start();
          generated.rules = FilePackRules.read(type, pack);
          if (fullLoadFiles != null) {
            fullLoadFiles.restore(generated, log);
          }
        } else if (pack.location().source() != PackSource.SERVER && pack.packId().startsWith(PackHelper.FILE_PACK_PREFIX)) {
          var rules = FilePackRules.read(type, pack);
          if (!rules.rules().isEmpty() || fullLoadFiles != null && fullLoadFiles.contains(pack.packId())) {
            log.start();
            var wrapper = GeneratedPackResources.wrap(type, pack);
            wrapper.rules = rules;
            prepared.set(index, wrapper);
            if (fullLoadFiles != null) {
              fullLoadFiles.restore(wrapper, log);
            }
          }
        }
      }
      return List.copyOf(prepared);
    } catch (RuntimeException | Error failure) {
      log.validationFailed(failure);
      notifyObservers(type, prepared, kind, failure);
      closeAfterFailure(prepared, failure);
      throw failure;
    }
  }

  /**
   * Closes candidate openings while preserving the original load failure.
   *
   * @param packs candidate resources to release.
   * @param failure original failure receiving any cleanup errors.
   */
  public static void closeAfterFailure(List<PackResources> packs, Throwable failure) {
    packs.forEach(pack -> closeAfterFailure(pack::close, failure));
  }

  /**
   * Runs native cleanup without replacing the original load failure.
   *
   * @param cleanup native owner cleanup.
   * @param failure original failure receiving any cleanup error.
   */
  public static void closeAfterFailure(Runnable cleanup, Throwable failure) {
    try {
      cleanup.run();
    } catch (RuntimeException | Error closeFailure) {
      if (closeFailure != failure) {
        failure.addSuppressed(closeFailure);
      }
    }
  }

  /**
   * Indicates whether mod initialization has closed pack and callback declarations.
   *
   * @return whether registrations have closed.
   */
  public static boolean declarationsClosed() {
    return declarationsClosed.get();
  }

  /**
   * Records the saved world behind a native pack repository.
   *
   * @param repository native data pack repository.
   * @param root saved world root.
   */
  public static void bindWorld(PackRepository repository, Path root) {
    WORLD_ROOTS.put(repository, root);
  }

  /**
   * Resolves cache storage for a saved world.
   *
   * @param repository data pack repository for this load.
   * @return world cache path, or {@code null} for a transient preview.
   */
  @Nullable
  public static Path worldCache(PackRepository repository) {
    Path world = WORLD_ROOTS.get(repository);
    return world == null ? null : worldCache(world);
  }

  /**
   * Resolves the data-pack cache beneath a saved world's root without creating directories.
   *
   * @param world saved world root.
   * @return world-local data-pack cache directory.
   */
  public static Path worldCache(Path world) {
    return world.resolve(Constants.MOD_ID).resolve("cache").resolve("data");
  }

  /**
   * Closes the callback declaration window before native loading.
   */
  public static void closeDeclarations() {
    if (declarationsClosed.compareAndSet(false, true) && Constants.LOGGER.isDebugEnabled()) {
      Services.PACK.logDeclarations();
      Services.REGISTRY.logEntryDeclarations();
    }
  }

  /**
   * Exposes the native client reload once generation has completed.
   *
   * @param reload future creating the native reload instance.
   * @param tracker native load tracker.
   * @return progress and result covering generation and native loading.
   */
  public static ReloadInstance gatedReload(CompletableFuture<ReloadInstance> reload, AttemptTracker tracker) {
    var done = tracker.watch(reload.thenCompose(ReloadInstance::done));
    return new ReloadInstance() {
      @Override
      public @NonNull CompletableFuture<?> done() {
        return done;
      }

      @Override
      public float getActualProgress() {
        return reload.isDone() && !reload.isCompletedExceptionally() ? reload.join().getActualProgress() : 0;
      }
    };
  }

  /**
   * Tracks the asynchronous native result and forwards caller cancellation to generation.
   */
  public static final class AttemptTracker {
    /**
     * Diagnostics shared with preparation and the generation attempt.
     */
    private final PackLoadLog log;

    /**
     * The generation attempt bound to the native future.
     */
    @Nullable
    private Attempt attempt;

    /**
     * Cancellation requested before the attempt could be bound, if any.
     */
    @Nullable
    private CancellationException cancellation;

    /**
     * @param log diagnostics for the native load.
     */
    public AttemptTracker(PackLoadLog log) {
      this.log = log;
    }

    /**
     * Returns the diagnostic context for this load.
     *
     * @return native load trace.
     */
    public PackLoadLog log() {
      return log;
    }

    /**
     * Associates the candidate once opened, propagating any earlier cancellation.
     *
     * @param opened candidate attempt.
     */
    public synchronized void bind(Attempt opened) {
      attempt = opened;
      if (cancellation != null) {
        opened.cancel(cancellation);
      }
    }

    /**
     * Observes the native result through a future which also cancels Cobweb's candidate.
     *
     * @param nativeResult native load completion.
     * @param <T> native result type.
     * @return observed result.
     */
    public <T> CompletableFuture<T> watch(CompletableFuture<T> nativeResult) {
      return watch(nativeResult, () -> {});
    }

    /**
     * Runs a successful-load action without losing cancellation forwarding.
     *
     * @param nativeResult native load completion.
     * @param applied action after generation is committed.
     * @param <T> native result type.
     * @return observed result including the action's failure, if any.
     */
    public <T> CompletableFuture<T> watch(CompletableFuture<T> nativeResult, Runnable applied) {
      var result = new CompletableFuture<T>() {
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
          synchronized (AttemptTracker.this) {
            if (!isDone()) {
              cancellation = new CancellationException("Resource load cancelled");
              if (attempt != null) {
                attempt.cancel(cancellation);
              } else {
                log.finish(cancellation);
              }
              return super.cancel(mayInterruptIfRunning);
            }
            return isCancelled();
          }
        }
      };
      nativeResult.whenComplete((value, failure) -> {
        synchronized (this) {
          if (cancellation == null) {
            if (attempt != null) {
              attempt.finish(failure);
            } else {
              log.finish(failure);
            }
            if (failure == null) {
              try {
                applied.run();
                result.complete(value);
              } catch (RuntimeException | Error actionFailure) {
                result.completeExceptionally(actionFailure);
              }
            } else {
              result.completeExceptionally(failure);
            }
          }
        }
      });
      return result;
    }
  }

  /**
   * Opens generation for a selected native resource stack.
   *
   * @param type native resource type.
   * @param packs selected native pack openings.
   * @param kind load kind.
   * @param cacheRoot directory for persistent callback contributions, if available.
   * @param repository repository providing native selection state.
   * @param log trace shared with pack preparation.
   * @return current attempt.
   */
  public static Attempt open(PackType type, List<PackResources> packs, LoadKind kind, @Nullable Path cacheRoot, PackRepository repository, PackLoadLog log) {
    List<GeneratedPackResources> generated = generated(packs.stream());
    closeDeclarations();
    var request = PackQueryLifecycle.begin(type, repository, packs);
    try {
      var required = Services.PACK.validateDependencies(type, packs);
      return new Attempt(type, List.copyOf(packs), generated, required, kind, cacheRoot, request, log);
    } catch (RuntimeException | Error failure) {
      log.validationFailed(failure);
      request.finish(false);
      notifyObservers(type, packs, kind, failure);
      throw failure;
    }
  }

  /**
   * Reports a load outcome once per participating native pack ID and logs observer failures.
   *
   * @param type native pack type.
   * @param packs selected native pack openings.
   * @param kind full load or reload.
   * @param failure cause of failure, or {@code null} on successful application.
   */
  private static void notifyObservers(PackType type, List<PackResources> packs, LoadKind kind, @Nullable Throwable failure) {
    packs.stream().map(PackResources::packId).distinct().forEach(id -> {
      var declaration = Services.PACK.packDeclaration(type, id);
      if (declaration != null && declaration.observer() != null) {
        try {
          declaration.observer().accept(new PackLoadOutcome(declaration.id(), type, kind, failure));
        } catch (RuntimeException | Error observerFailure) {
          Constants.LOGGER.error("Pack outcome observer failed for {}", id, observerFailure);
        }
      }
    });
  }

  /**
   * Retains the full-load output of an active world, independently of later selections.
   */
  public static final class FullLoadFiles implements AutoCloseable {
    /**
     * The full-load snapshots retained by the running server.
     */
    private final Map<Object, GeneratedPackResources> retained = new HashMap<>();

    /**
     * Whether the applied full-load set is already pinned or the server has stopped.
     */
    private boolean captured;

    public FullLoadFiles() {}

    /**
     * Pins the first applied full-load files before the resource manager can be replaced.
     *
     * @param resources currently applied native resources of this world.
     */
    public synchronized void capture(ResourceManager resources) {
      if (!captured) {
        generated(resources.listPacks()).forEach(pack -> {
          var snapshot = pack.fullLoadSnapshot();
          if (snapshot.hasFiles()) {
            retained.put(pack.ownerKey(), snapshot);
          } else {
            snapshot.close();
          }
        });
        captured = true;
      }
    }

    /**
     * Restores retained full-load backing to the matching selected opening.
     *
     * @param pack currently selected pack opening.
     * @param log diagnostics for the reload.
     */
    private synchronized void restore(GeneratedPackResources pack, PackLoadLog log) {
      var earlier = retained.get(pack.ownerKey());
      if (earlier != null) {
        pack.retainFullLoad(earlier);
        if (Constants.LOGGER.isDebugEnabled()) {
          log.debug("retained full-load files for " + pack.packId());
        }
      }
    }

    /**
     * Checks whether the running world retains full-load files for a native ID.
     *
     * @param id native ID of an ordinary file pack.
     * @return whether files are retained for that ID.
     */
    private synchronized boolean contains(String id) {
      return retained.containsKey(id);
    }

    /**
     * Releases this world's full-load file leases when the server stops.
     */
    @Override
    public synchronized void close() {
      captured = true;
      retained.values().forEach(GeneratedPackResources::close);
      retained.clear();
    }
  }

  /**
   * Finds the attempt that owns a native resource manager.
   *
   * @param manager native manager.
   * @return associated attempt, if one exists.
   */
  @Nullable
  private static Attempt find(ResourceManager manager) {
    return generated(manager.listPacks()).stream().map(pack -> pack.attempt).filter(Objects::nonNull).findFirst().orElse(null);
  }

  /**
   * Records prepared tag members before Vanilla applies its pending tags.
   *
   * @param manager native manager.
   * @param tags decoded members for a registry.
   */
  public static void captureTags(ResourceManager manager, TagLoader.LoadResult<?> tags) {
    Attempt attempt = find(manager);
    if (attempt != null) {
      tags.tags().forEach((tag, members) -> attempt.tags.put(tag, List.copyOf(members)));
    }
  }

  /**
   * Runs any generation needed before a native registry batch.
   *
   * @param manager native manager.
   * @param available native registry lookups.
   * @param definitions registry batch to decode.
   * @return completion of applicable generation.
   */
  public static CompletableFuture<Void> beforeBatch(ResourceManager manager, List<HolderLookup.RegistryLookup<?>> available, List<RegistryDataLoader.RegistryData<?>> definitions) {
    Attempt attempt = find(manager);
    if (attempt != null && attempt.kind == LoadKind.FULL_LOAD && contains(definitions, Registries.ENCHANTMENT)) {
      return attempt.advance(Phase.STATIC_TAGS_READY, HolderLookup.Provider.create(available.stream()));
    }
    return CompletableFuture.completedFuture(null);
  }

  /**
   * Runs generation once a native registry batch is frozen.
   *
   * @param manager native manager.
   * @param available native lookups preceding the batch.
   * @param definitions decoded registry batch.
   * @param decoded native frozen result.
   * @return completion with the unchanged native registry batch.
   */
  public static CompletableFuture<RegistryAccess.Frozen> afterBatch(ResourceManager manager, List<HolderLookup.RegistryLookup<?>> available, List<RegistryDataLoader.RegistryData<?>> definitions, RegistryAccess.Frozen decoded) {
    Attempt attempt = find(manager);
    if (attempt != null) {
      Phase phase;
      if (contains(definitions, Registries.ENCHANTMENT)) {
        phase = Phase.WORLD_REGISTRIES_READY;
      } else {
        phase = contains(definitions, Registries.RECIPE) ? Phase.RELOADABLE_REGISTRIES_READY : null;
      }
      if (phase != null) {
        var lookups = HolderLookup.Provider.create(Stream.concat(available.stream(), decoded.listRegistries()));
        return attempt.advance(phase, lookups).thenApply(ignored -> decoded);
      }
    }
    return CompletableFuture.completedFuture(decoded);
  }

  /**
   * Runs generation before the native reloadable registry reader.
   *
   * @param manager native manager.
   * @param layers prepared world and dimension registries.
   * @param tags staged tags of existing registries.
   * @return completion of generation before native decoding.
   */
  public static CompletableFuture<Void> beforeReloadable(ResourceManager manager, LayeredRegistryAccess<RegistryLayer> layers, List<Registry.PendingTags<?>> tags) {
    Attempt attempt = find(manager);
    if (attempt != null) {
      var lookups = HolderLookup.Provider.create(TagLoader.buildUpdatedLookups(layers.getAccessForLoading(RegistryLayer.RELOADABLE), tags).stream());
      return attempt.advance(Phase.WORLD_CONTEXT_READY, lookups);
    }
    return CompletableFuture.completedFuture(null);
  }

  /**
   * Checks whether the native batch contains a registry.
   *
   * @param definitions native registry definitions.
   * @param key native registry key.
   * @return whether the batch contains the key.
   */
  private static boolean contains(List<RegistryDataLoader.RegistryData<?>> definitions, Object key) {
    return definitions.stream().anyMatch(definition -> definition.key().equals(key));
  }

  /**
   * Collects generated pack openings from the native resource stack.
   *
   * @param packs stream of selected pack openings.
   * @return generation-aware openings in stack order.
   */
  private static List<GeneratedPackResources> generated(Stream<PackResources> packs) {
    return packs.filter(GeneratedPackResources.class::isInstance).map(GeneratedPackResources.class::cast).filter(GeneratedPackResources::generates).toList();
  }

  /**
   * Holds one selected stack and its candidate generated content.
   */
  public static final class Attempt {
    /**
     * Diagnostics shared with pack preparation and native application.
     */
    private final PackLoadLog log;

    /**
     * Native resource type of this attempt.
     */
    private final PackType type;

    /**
     * Selected native pack openings in native precedence order.
     */
    private final List<PackResources> packs;

    /**
     * Whether this native load is a full load or reload.
     */
    private final LoadKind kind;

    /**
     * Requested pack selection tracked until native application completes.
     */
    private final PackQueryLifecycle.Request request;

    /**
     * Compiled generation settings for the selected stack.
     */
    private final GenerationPlan plan;

    /**
     * Prepared native tags captured as they become available.
     */
    private final Map<TagKey<?>, List<? extends Holder<?>>> tags = new HashMap<>();

    /**
     * Assigns an order to submissions from Java and file rules.
     */
    private final AtomicLong sequence = new AtomicLong();

    /**
     * Prevents multiple native outcomes from publishing state or notifying observers again.
     */
    private final AtomicBoolean reported = new AtomicBoolean();

    /**
     * Cache scope for this load, when available.
     */
    @Nullable
    private final Path cacheRoot;

    /**
     * Pending cache updates committed only after native application succeeds.
     */
    private final Collection<CacheUpdate> cacheUpdates = new ConcurrentLinkedQueue<>();

    /**
     * Phases that have already started in this attempt.
     */
    private final Set<Phase> started = EnumSet.noneOf(Phase.class);

    /**
     * Whether a native pack opening has released this attempt.
     */
    private volatile boolean closed;

    /**
     * Cancellation signal raced against generation work.
     */
    private final CompletableFuture<Void> aborted = new CompletableFuture<>();

    /**
     * Callback contexts still open in this attempt.
     */
    private final Set<GenerationContext> inputs = ConcurrentHashMap.newKeySet();

    /**
     * Stage resource captures still open in this attempt.
     */
    private final Set<StageResourceSnapshots> stageSnapshots = ConcurrentHashMap.newKeySet();

    /**
     * Whether native application has completed successfully.
     */
    private volatile boolean applied;

    /**
     * Native feature flags captured when generation starts.
     */
    private FeatureFlagSet features = FeatureFlagSet.of();

    /**
     * @param type native resource type.
     * @param packs selected pack openings.
     * @param generated openings that can participate in generation.
     * @param required validated required predecessor IDs by native pack ID.
     * @param kind full load or reload.
     * @param cacheRoot physical cache scope, or {@code null} when unavailable.
     * @param request requested selection tracked by this attempt.
     * @param log diagnostics for this native load.
     */
    private Attempt(PackType type, List<PackResources> packs, List<GeneratedPackResources> generated, Map<String, List<String>> required, LoadKind kind, @Nullable Path cacheRoot, PackQueryLifecycle.Request request, PackLoadLog log) {
      this.type = type;
      this.packs = packs;
      this.kind = kind;
      this.cacheRoot = cacheRoot;
      this.request = request;
      this.log = log;
      plan = new GenerationPlan(type, generated, required, kind, cacheRoot);
      packs.stream().filter(GeneratedPackResources.class::isInstance).map(GeneratedPackResources.class::cast).forEach(pack -> pack.attempt = this);
    }

    /**
     * Runs the first phase of this resource load.
     *
     * @param registryAccess currently available native registries.
     * @param enabledFeatures native features selected for this data load, or empty for client assets.
     * @return completion of the early generation stages.
     */
    public CompletableFuture<Void> begin(HolderLookup.Provider registryAccess, FeatureFlagSet enabledFeatures) {
      features = enabledFeatures;
      return advance(type == PackType.SERVER_DATA ? Phase.DATA_RESOURCES : Phase.CLIENT_RESOURCES, registryAccess);
    }

    /**
     * Marks this attempt closed and releases its active callback and stage views.
     */
    void markClosed() {
      closed = true;
      inputs.forEach(GenerationContext::close);
      stageSnapshots.forEach(StageResourceSnapshots::close);
    }

    /**
     * Signals cancellation, closes active inputs and reports the aborted load.
     *
     * @param failure cancellation raised by the native load.
     */
    private void cancel(CancellationException failure) {
      aborted.completeExceptionally(failure);
      inputs.forEach(GenerationContext::close);
      stageSnapshots.forEach(StageResourceSnapshots::close);
      finish(failure);
    }

    /**
     * Checks that the attempt still accepts work.
     *
     * @throws CancellationException if the attempt has closed or been canceled.
     */
    private void checkActive() {
      if (closed || aborted.isDone()) {
        throw new CancellationException("Resource load no longer accepts generated contributions");
      }
    }

    /**
     * Indicates whether the native manager already released its generated resources.
     *
     * @return whether the candidate is closed.
     */
    public boolean closed() {
      return closed;
    }

    /**
     * Indicates whether the selected stack has generated pack openings.
     *
     * @return whether Cobweb owns generated files for this attempt.
     */
    public boolean generates() {
      return !plan.order.isEmpty();
    }

    /**
     * Indicates whether this native load contains Cobweb-owned resource openings.
     *
     * @return whether a rejected load may need early cleanup.
     */
    public boolean hasRegisteredPacks() {
      return packs.stream().anyMatch(GeneratedPackResources.class::isInstance);
    }

    /**
     * Indicates whether native application has completed successfully.
     *
     * @return whether this attempt applied its resources.
     */
    boolean applied() {
      return applied;
    }

    /**
     * Reports the native result after its full application or failure.
     *
     * @param failure load failure, or {@code null} on success.
     */
    public void finish(@Nullable Throwable failure) {
      if (reported.compareAndSet(false, true)) {
        applied = failure == null;
        request.finish(applied);
        if (applied) {
          cacheUpdates.forEach(update -> {
            try {
              CallbackCache.apply(update.address, update.batch, update.hit, update.persistent);
            } catch (RuntimeException cacheFailure) {
              Constants.LOGGER.error("Unable to publish callback cache {}", update.address, cacheFailure);
            }
          });
        }
        cacheUpdates.clear();
        if (applied) {
          plan.order.keySet().forEach(GeneratedPackResources::releaseSuperseded);
        }
        log.finish(failure);
        notifyObservers(type, packs, kind, failure);
      }
    }

    /**
     * Runs a native phase at most once and waits for each of its stages in order.
     *
     * @param phase active generation phase.
     * @param registries native registry lookups available in this phase.
     * @return completion of the phase, or the cancellation signal.
     */
    private synchronized CompletableFuture<Void> advance(Phase phase, HolderLookup.Provider registries) {
      if (!aborted.isDone()) {
        if (started.add(phase)) {
          CompletableFuture<Void> stages = CompletableFuture.completedFuture(null);
          for (Stage stage : Stage.values()) {
            stages = stages.thenCompose(ignored -> runStage(phase, stage, registries));
          }
          return CompletableFuture.anyOf(stages, aborted).thenRun(this::checkActive);
        }
        return CompletableFuture.completedFuture(null);
      }
      return aborted;
    }

    /**
     * Builds the stage baseline and waits for each pack's predecessors before giving it a resource view.
     * Publishes that pack's merged submissions after all of its tasks finish.
     *
     * @param phase active generation phase.
     * @param stage active generation stage.
     * @param registries native registry lookups available in this phase.
     * @return completion of all generation scheduled for the stage.
     */
    private CompletableFuture<Void> runStage(Phase phase, Stage stage, HolderLookup.Provider registries) {
      var snapshots = new StageResourceSnapshots(type);
      stageSnapshots.add(snapshots);
      if (closed || aborted.isDone()) {
        snapshots.close();
        stageSnapshots.remove(snapshots);
        checkActive();
      }
      Map<PackResources, PackResources> baseline = new IdentityHashMap<>();
      packs.forEach(pack -> baseline.put(pack, pack instanceof GeneratedPackResources generated ? generated.snapshot(snapshots) : snapshots.wrap(pack)));
      Map<GeneratedPackResources, CompletableFuture<Void>> finished = new LinkedHashMap<>();
      long startedAt = 0;
      for (var entry : plan.order.entrySet()) {
        GeneratedPackResources pack = entry.getKey();
        List<Task> tasks = pack.javaTasks().stream().filter(task -> task.phases().get(kind) == phase && task.stage() == stage).toList();
        var rules = plan.fileTasks.get(pack).stream().filter(task -> task.phases().get(kind) == phase && task.stage() == stage).toList();
        CompletableFuture<Void> ready = all(entry.getValue().stream().map(finished::get).toList());
        finished.put(pack, ready);
        if (!tasks.isEmpty() || !rules.isEmpty()) {
          if (startedAt == 0 && Constants.LOGGER.isDebugEnabled()) {
            startedAt = System.nanoTime();
            Constants.LOGGER.debug("{} started {}/{}", log.label(), phase, stage);
          }
          var predecessors = Set.copyOf(entry.getValue());
          finished.put(pack, ready.thenCompose(ignored -> {
            // This manager borrows shared stage views; closing it here would invalidate asynchronous readers.
            // The stage releases captured file backing after all participating callbacks finish.
            var snapshot = new MultiPackResourceManager(
              type,
              packs.stream().map(source -> source instanceof GeneratedPackResources generated && predecessors.contains(generated) ? generated.snapshot(snapshots) : baseline.get(source)).toList()
            );
            Map<Identifier, ConcurrentLinkedQueue<ContributionMerger.Submission>> submissions = new ConcurrentHashMap<>();
            var runs = new ArrayList<CompletableFuture<Void>>();
            tasks.forEach(task -> runs.add(invoke(pack, task, phase, snapshot, registries, submissions)));
            CompletableFuture<Void> ordered = CompletableFuture.completedFuture(null);
            for (var rule : rules) {
              ordered = ordered.thenCompose(result -> invoke(pack, rule, phase, snapshot, registries, submissions));
            }
            runs.add(ordered);
            return all(runs).thenRun(() -> publishStage(pack, phase, stage, snapshots, submissions));
          }));
        }
      }
      long traceStartedAt = startedAt;
      return all(finished.values()).whenComplete((ignored, failure) -> {
        snapshots.close();
        stageSnapshots.remove(snapshots);
        if (traceStartedAt != 0) {
          Constants.LOGGER.debug("{} {} {}/{} in {} ms", log.label(), failure == null ? "completed" : "stopped", phase, stage, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - traceStartedAt));
        }
      });
    }

    /**
     * Publishes one pack's merged stage output while its load still accepts changes.
     *
     * @param pack owning pack opening.
     * @param phase active native phase.
     * @param stage completed generation stage.
     * @param snapshots stage captures used to resolve aggregate bases.
     * @param submissions completed contributions by destination file.
     */
    private void publishStage(GeneratedPackResources pack, Phase phase, Stage stage, StageResourceSnapshots snapshots, Map<Identifier, ConcurrentLinkedQueue<ContributionMerger.Submission>> submissions) {
      if (!closed && !aborted.isDone()) {
        Map<Identifier, GeneratedPackResources.File> files = new HashMap<>();
        submissions.forEach((id, operations) -> files.put(id, ContributionMerger.merge(pack, id, operations, phase, stage, snapshots)));
        pack.publish(files);
      }
    }

    /**
     * Runs one task against its stable view, replaying a verified cache batch when possible.
     *
     * @param pack owning pack opening.
     * @param task task to run.
     * @param phase active native phase.
     * @param manager pack's stable resource view for this stage.
     * @param registries available registry lookups.
     * @param submissions stage submissions for the owning pack.
     * @return completion of cache replay or the callback and its returned asynchronous work.
     */
    private CompletableFuture<Void> invoke(GeneratedPackResources pack, Task task, Phase phase, ResourceManager manager, HolderLookup.Provider registries, Map<Identifier, ConcurrentLinkedQueue<ContributionMerger.Submission>> submissions) {
      Map<CapturedInput<?>, Object> values = new IdentityHashMap<>();
      task.inputs().values().forEach(input -> {
        if (input instanceof CapturedInput<?> capture) {
          values.put(capture, plan.captured.get(capture));
        }
      });
      var caching = plan.caching.get(task);
      var policy = caching.callback();
      boolean persistent = caching.mode() == TaskCachePolicy.Mode.PERSISTENT;
      Path address = caching.mode() == TaskCachePolicy.Mode.DISABLED ? null : CallbackCache.address(cacheRoot, pack, task, policy);
      var observations = new CacheObservations(manager, request.queries(), features, address != null);
      Identifier owner = pack.logicalId == null ? Identifier.fromNamespaceAndPath(Constants.MOD_ID, "file_pack") : pack.logicalId;
      var scope = new GenerationScope(owner, phase, task.stage(), kind, request.queries(), features);
      var context = new GenerationContext(scope, manager, registries, values, tags, observations);
      inputs.add(context);
      Collection<Recorded> recorded = new ConcurrentLinkedQueue<>();
      var output = new GenerationOutput(context, contribution -> submit(pack, task, phase, submissions, recorded, contribution));
      return CompletableFuture.supplyAsync(
        () -> {
          checkActive();
          log.task(task.fileRule());
          String fingerprint = null;
          if (address != null) {
            try {
              fingerprint = CallbackCache.fingerprint(task, context, values, policy, persistent);
            } catch (RuntimeException failure) {
              Constants.LOGGER.error("Cannot fingerprint callback {} in {}", task.index(), pack.packId(), failure);
            }
          }
          if (address != null && fingerprint != null) {
            var hit = CallbackCache.read(address, fingerprint, observations, reason -> log.cacheMiss(pack, task, reason));
            if (hit != null) {
              if (replay(pack, task, phase, submissions, recorded, hit)) {
                log.cacheHit();
                cacheUpdates.add(new CacheUpdate(address, hit, true, persistent));
                return CompletableFuture.<Void>completedFuture(null);
              }
              log.cacheMiss(pack, task, "cached contribution failed validation");
            }
          } else if (address != null) {
            log.cacheMiss(pack, task, "callback fingerprint unavailable");
          } else if (task.fileRule() || policy != null) {
            log.cacheMiss(pack, task, "cache scope unavailable for this load");
          }
          try {
            CompletionStage<Void> result = Objects.requireNonNull(task.callback().generate(context, output), "Generation completion stage");
            if (address != null) {
              var cache = new CacheProof(address, fingerprint, persistent);
              return result.thenRun(() -> recordCache(pack, task, cache, observations, recorded));
            }
            return result;
          } catch (Exception failure) {
            return CompletableFuture.<Void>failedFuture(failure);
          }
        },
        Util.backgroundExecutor()
      ).thenCompose(CompletionStage::toCompletableFuture).handle((ignored, failure) -> {
        context.close();
        inputs.remove(context);
        if (failure != null) {
          throw new IllegalStateException(log.label() + ": pack " + pack.packId() + " " + (task.fileRule() ? "rule " : "callback ") + task.index() + " failed during " + phase + "/" + task.stage(), failure);
        }
        return null;
      });
    }

    /**
     * Records a completed callback batch for adoption only after native load success.
     * Leaves unverifiable output uncached while preserving the applied-baseline update.
     *
     * @param pack owning pack opening.
     * @param task completed callback or file rule.
     * @param cache cache address and callback fingerprint.
     * @param observations input observations collected by the callback.
     * @param recorded contributions in submission order.
     */
    private void recordCache(GeneratedPackResources pack, Task task, CacheProof cache, CacheObservations observations, Collection<Recorded> recorded) {
      CallbackCache.Batch batch = null;
      if (cache.fingerprint() != null && observations.validate()) {
        var contributions = recorded.stream().sorted(Comparator.comparingLong(Recorded::sequence)).map(Recorded::contribution).toList();
        batch = new CallbackCache.Batch(UUID.randomUUID().toString(), cache.fingerprint(), observations.entries(), contributions);
      } else {
        Constants.LOGGER.debug("{} could not cache {} {} in {} because an input could not be verified", log.label(), task.origin().label(), task.index(), pack.packId());
      }
      cacheUpdates.add(new CacheUpdate(cache.address(), batch, false, cache.persistent()));
    }

    /**
     * Identifies the record and fingerprint of one callback's successful output.
     *
     * @param address record path in the current cache scope.
     * @param fingerprint callback fingerprint, or {@code null} when unavailable.
     * @param persistent whether the identity survives a process restart.
     */
    private record CacheProof(Path address, @Nullable String fingerprint, boolean persistent) {}

    /**
     * Validates, records and adopts a completed contribution.
     *
     * @param pack owning pack opening.
     * @param task task that submitted the contribution.
     * @param phase active native phase.
     * @param submissions stage submissions for the owning pack.
     * @param recorded callback's ordered contribution log.
     * @param contribution completed contribution whose backing is transferred.
     */
    private void submit(GeneratedPackResources pack, Task task, Phase phase, Map<Identifier, ConcurrentLinkedQueue<ContributionMerger.Submission>> submissions, Collection<Recorded> recorded, PackContribution contribution) {
      validateSubmission(pack, task, phase, contribution);
      record(task, submissions, recorded, contribution);
      pack.adoptBatch(List.of(contribution));
    }

    /**
     * Assigns a submission order and records both the merge operations and the replayable contribution.
     *
     * @param task task that submitted the contribution.
     * @param submissions stage submissions for the owning pack.
     * @param recorded callback's ordered contribution log.
     * @param contribution completed contribution.
     */
    private void record(Task task, Map<Identifier, ConcurrentLinkedQueue<ContributionMerger.Submission>> submissions, Collection<Recorded> recorded, PackContribution contribution) {
      long order = sequence.incrementAndGet();
      ContributionMerger.Operation operation = switch (contribution) {
        case PackContribution.File file -> new ContributionMerger.Replacement(file.contents(), false);
        case PackContribution.Tag tag -> new ContributionMerger.TagAddition(tag.addition());
        case PackContribution.Member member -> new ContributionMerger.Member(member.name(), member.contents());
        case PackContribution.WithMetadata grouped -> new ContributionMerger.Replacement(grouped.contents(), false);
      };
      submissions.computeIfAbsent(contribution.id(), ignored -> new ConcurrentLinkedQueue<>()).add(new ContributionMerger.Submission(order, task, operation));
      if (contribution instanceof PackContribution.WithMetadata grouped) {
        ContributionMerger.Operation metadata = grouped.metadata() == null ? new ContributionMerger.ClearMetadata() : new ContributionMerger.Replacement(grouped.metadata(), true);
        submissions.computeIfAbsent(grouped.metadataId(), ignored -> new ConcurrentLinkedQueue<>()).add(new ContributionMerger.Submission(order, task, metadata));
      }
      recorded.add(new Recorded(order, contribution));
    }

    /**
     * Validates and adopts a cached batch before restoring its submissions to the stage.
     *
     * @param pack owning pack opening.
     * @param task task associated with the cache batch.
     * @param phase active native phase.
     * @param submissions stage submissions for the owning pack.
     * @param recorded callback's ordered contribution log.
     * @param batch cached batch with retained contribution backing.
     * @return whether the batch was replayed.
     */
    private boolean replay(GeneratedPackResources pack, Task task, Phase phase, Map<Identifier, ConcurrentLinkedQueue<ContributionMerger.Submission>> submissions, Collection<Recorded> recorded, CallbackCache.Batch batch) {
      try {
        batch.values().forEach(contribution -> validateSubmission(pack, task, phase, contribution));
        pack.adoptBatch(batch.values());
      } catch (RuntimeException failure) {
        batch.values().forEach(PackContribution::release);
        Constants.LOGGER.error("Invalid cached contribution in {}", pack.packId(), failure);
        return false;
      }
      batch.values().forEach(contribution -> record(task, submissions, recorded, contribution));
      return true;
    }

    /**
     * Checks namespace authorization, attempt state and the native output contract.
     *
     * @param pack owning pack opening.
     * @param task task that submitted the contribution.
     * @param phase active native phase.
     * @param contribution completed contribution to validate.
     * @throws IllegalArgumentException if the namespace or output contract is invalid.
     * @throws CancellationException if the load no longer accepts writes.
     */
    private void validateSubmission(GeneratedPackResources pack, Task task, Phase phase, PackContribution contribution) {
      Identifier id = contribution.id();
      if (!task.fileRule() && !Objects.requireNonNull(pack.definition).generation().namespaces.contains(id.getNamespace())) {
        throw new IllegalArgumentException("Undeclared resource namespace in " + pack.packId() + ": " + id);
      }
      checkActive();
      // A grouped sidecar inherits the validated file's deadline and lifetime.
      plan.checkOutput(task, phase, id);
    }

    /**
     * Joins completion of generation tasks.
     *
     * @param futures generation task completions.
     * @return future that completes when all supplied futures complete.
     */
    private static CompletableFuture<Void> all(Collection<CompletableFuture<Void>> futures) {
      return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }
  }

  /**
   * Records a cache baseline change to apply only after native success.
   *
   * @param address address of the cache record.
   * @param batch validated batch, or {@code null} when reuse cannot be established.
   * @param hit whether the old batch was reused.
   * @param persistent whether the cache record can persist across launches.
   */
  private record CacheUpdate(Path address, @Nullable CallbackCache.Batch batch, boolean hit, boolean persistent) {}

  /**
   * Keeps a replayable contribution in submission order.
   *
   * @param sequence attempt-wide submission index.
   * @param contribution complete submitted contribution.
   */
  private record Recorded(long sequence, PackContribution contribution) {}
}
