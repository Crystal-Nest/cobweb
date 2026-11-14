package it.crystalnest.cobweb.api.pack.generation;

import it.crystalnest.cobweb.Constants;
import it.crystalnest.cobweb.api.pack.dependency.NativePackDependencies;
import it.crystalnest.cobweb.api.pack.generation.PackGeneration.Task;
import it.crystalnest.cobweb.api.pack.generation.input.CapturedInput;
import it.crystalnest.cobweb.api.pack.generation.lifecycle.LoadKind;
import it.crystalnest.cobweb.api.pack.generation.lifecycle.PackLoadOutcome;
import it.crystalnest.cobweb.api.pack.generation.lifecycle.Phase;
import it.crystalnest.cobweb.api.pack.generation.lifecycle.Stage;
import it.crystalnest.cobweb.api.pack.query.PackQueryLifecycle;
import it.crystalnest.cobweb.platform.Services;
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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Coordinates generated pack content with the native resource-loading boundaries.
 */
@ApiStatus.Internal
public final class DynamicPackLoads {
  private static final Map<PackRepository, Path> WORLD_ROOTS = java.util.Collections.synchronizedMap(new WeakHashMap<>());

  private static volatile boolean declarationsClosed;

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
   * @return openings in unchanged native priority order.
   * @throws IllegalArgumentException if a selected file rule manifest is invalid.
   */
  public static List<PackResources> prepare(PackType type, List<PackResources> packs, LoadKind kind, @Nullable FullLoadFiles fullLoadFiles) {
    var prepared = new ArrayList<>(packs);
    try {
      for (int index = 0; index < prepared.size(); index++) {
        var pack = prepared.get(index);
        if (pack instanceof GeneratedPackResources generated) {
          generated.rules = FilePackRules.read(type, pack);
          if (fullLoadFiles != null) {
            fullLoadFiles.restore(generated);
          }
        } else if (pack.location().source() != PackSource.SERVER && pack.packId().startsWith("file/")) {
          var rules = FilePackRules.read(type, pack);
          if (!rules.rules().isEmpty() || fullLoadFiles != null && fullLoadFiles.contains(pack.packId())) {
            var wrapper = GeneratedPackResources.wrap(type, pack);
            wrapper.rules = rules;
            prepared.set(index, wrapper);
            if (fullLoadFiles != null) {
              fullLoadFiles.restore(wrapper);
            }
          }
        }
      }
      return List.copyOf(prepared);
    } catch (RuntimeException | Error failure) {
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
    return declarationsClosed;
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
    return world == null ? null : world.resolve("cobweb/cache/data");
  }

  /**
   * Closes the callback declaration window before native loading.
   */
  public static void closeDeclarations() {
    declarationsClosed = true;
    NativePackDependencies.closeDeclarations();
  }

  /**
   * Exposes the native client reload once generation has completed.
   *
   * @param reload future creating the native reload instance.
   * @param attempt generated pack attempt, if opened.
   * @return progress and result covering generation and native loading.
   */
  public static ReloadInstance gatedReload(CompletableFuture<ReloadInstance> reload, @Nullable Attempt attempt) {
    var tracker = new AttemptTracker();
    if (attempt != null) {
      tracker.bind(attempt);
    }
    var done = tracker.watch(reload.thenCompose(ReloadInstance::done));
    return new ReloadInstance() {
      @Override
      public CompletableFuture<?> done() {
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
    @Nullable
    private Attempt attempt;

    @Nullable
    private CancellationException cancellation;

    /**
     * Creates a tracker before native pack openings are available.
     */
    public AttemptTracker() {}

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
            if (isDone()) {
              return isCancelled();
            }
            cancellation = new CancellationException("Resource load cancelled");
            if (attempt != null) {
              attempt.cancel(cancellation);
            }
            return super.cancel(mayInterruptIfRunning);
          }
        }
      };
      nativeResult.whenComplete((value, failure) -> {
        synchronized (this) {
          if (cancellation == null) {
            if (attempt != null) {
              attempt.finish(failure);
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
   * @return current attempt.
   */
  public static Attempt open(PackType type, List<PackResources> packs, LoadKind kind, @Nullable Path cacheRoot, PackRepository repository) {
    List<GeneratedPackResources> generated = generated(packs.stream());
    closeDeclarations();
    var request = PackQueryLifecycle.begin(type, repository, packs);
    try {
      var required = NativePackDependencies.validate(type, packs);
      return new Attempt(type, List.copyOf(packs), generated, required, kind, cacheRoot, request);
    } catch (RuntimeException | Error failure) {
      request.finish(false);
      notifyObservers(type, packs, kind, failure);
      throw failure;
    }
  }

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
    private final Map<Object, GeneratedPackResources> retained = new HashMap<>();

    private boolean captured;

    /**
     * Creates storage for one running server.
     */
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

    private synchronized void restore(GeneratedPackResources pack) {
      var earlier = retained.get(pack.ownerKey());
      if (earlier != null) {
        pack.retainFullLoad(earlier);
      }
    }

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
  public static Attempt find(ResourceManager manager) {
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
  public static CompletableFuture<RegistryAccess.Frozen> afterBatch(ResourceManager manager, List<HolderLookup.RegistryLookup<?>> available,
    List<RegistryDataLoader.RegistryData<?>> definitions, RegistryAccess.Frozen decoded) {
    Attempt attempt = find(manager);
    if (attempt == null) {
      return CompletableFuture.completedFuture(decoded);
    }
    Phase phase = contains(definitions, Registries.ENCHANTMENT) ? Phase.WORLD_REGISTRIES_READY
      : contains(definitions, Registries.RECIPE) ? Phase.RELOADABLE_REGISTRIES_READY : null;
    if (phase == null) {
      return CompletableFuture.completedFuture(decoded);
    }
    var lookups = HolderLookup.Provider.create(Stream.concat(available.stream(), decoded.listRegistries()));
    return attempt.advance(phase, lookups).thenApply(ignored -> decoded);
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
    if (attempt == null) {
      return CompletableFuture.completedFuture(null);
    }
    var lookups = HolderLookup.Provider.create(TagLoader.buildUpdatedLookups(layers.getAccessForLoading(RegistryLayer.RELOADABLE), tags).stream());
    return attempt.advance(Phase.WORLD_CONTEXT_READY, lookups);
  }

  private static boolean contains(List<RegistryDataLoader.RegistryData<?>> definitions, Object key) {
    return definitions.stream().anyMatch(definition -> definition.key().equals(key));
  }

  private static List<GeneratedPackResources> generated(Stream<PackResources> packs) {
    return packs.filter(GeneratedPackResources.class::isInstance).map(GeneratedPackResources.class::cast).filter(GeneratedPackResources::generates).toList();
  }

  /**
   * Holds one selected stack and its candidate generated content.
   */
  public static final class Attempt {
    private final PackType type;

    private final List<PackResources> packs;

    private final LoadKind kind;

    private final PackQueryLifecycle.Request request;

    private final GenerationPlan plan;

    private final Map<TagKey<?>, List<? extends Holder<?>>> tags = new HashMap<>();

    private final AtomicLong sequence = new AtomicLong();

    private final AtomicBoolean reported = new AtomicBoolean();

    @Nullable
    private final Path cacheRoot;

    private final Collection<CacheUpdate> cacheUpdates = new ConcurrentLinkedQueue<>();

    private final Set<Phase> started = new HashSet<>();

    private volatile boolean closed;

    private final CompletableFuture<Void> aborted = new CompletableFuture<>();

    private final Set<GenerationContext> inputs = ConcurrentHashMap.newKeySet();

    private final Set<StageResourceSnapshots> stageSnapshots = ConcurrentHashMap.newKeySet();

    private volatile boolean applied;

    private FeatureFlagSet features = FeatureFlagSet.of();

    private Attempt(PackType type, List<PackResources> packs, List<GeneratedPackResources> generated, Map<String, List<String>> required, LoadKind kind,
      @Nullable Path cacheRoot, PackQueryLifecycle.Request request) {
      this.type = type;
      this.packs = packs;
      this.kind = kind;
      this.cacheRoot = cacheRoot;
      this.request = request;
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

    void markClosed() {
      closed = true;
      inputs.forEach(GenerationContext::close);
      stageSnapshots.forEach(StageResourceSnapshots::close);
    }

    private void cancel(CancellationException failure) {
      aborted.completeExceptionally(failure);
      inputs.forEach(GenerationContext::close);
      stageSnapshots.forEach(StageResourceSnapshots::close);
      finish(failure);
    }

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

    boolean applied() {
      return applied;
    }

    /**
     * Reports the native result after its full application or failure.
     *
     * @param failure load failure, or {@code null} on success.
     */
    public void finish(@Nullable Throwable failure) {
      if (!reported.compareAndSet(false, true)) {
        return;
      }
      applied = failure == null;
      request.finish(applied);
      if (applied) {
        cacheUpdates.forEach(update -> {
          try {
            CallbackCache.apply(update.address, update.batch, update.hit, update.persistent);
          } catch (RuntimeException cacheFailure) {
            Constants.LOGGER.warn("Unable to publish callback cache {}", update.address, cacheFailure);
          }
        });
      }
      cacheUpdates.clear();
      if (applied) {
        plan.order.keySet().forEach(GeneratedPackResources::releaseSuperseded);
      }
      notifyObservers(type, packs, kind, failure);
    }

    private synchronized CompletableFuture<Void> advance(Phase phase, HolderLookup.Provider registries) {
      if (aborted.isDone()) {
        return aborted;
      }
      if (!started.add(phase)) {
        return CompletableFuture.completedFuture(null);
      }
      CompletableFuture<Void> stages = CompletableFuture.completedFuture(null);
      for (Stage stage : Stage.values()) {
        stages = stages.thenCompose(ignored -> runStage(phase, stage, registries));
      }
      return CompletableFuture.anyOf(stages, aborted).thenRun(this::checkActive);
    }

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
      for (var entry : plan.order.entrySet()) {
        GeneratedPackResources pack = entry.getKey();
        List<Task> tasks = pack.javaTasks().stream().filter(task -> task.phases().get(kind) == phase && task.stage() == stage).toList();
        var rules = plan.fileTasks.get(pack).stream().filter(task -> task.phases().get(kind) == phase && task.stage() == stage).toList();
        CompletableFuture<Void> ready = all(entry.getValue().stream().map(finished::get).toList());
        if (tasks.isEmpty() && rules.isEmpty()) {
          finished.put(pack, ready);
          continue;
        }
        var predecessors = Set.copyOf(entry.getValue());
        finished.put(pack, ready.thenCompose(ignored -> {
          var snapshot = new MultiPackResourceManager(type, packs.stream().map(source -> source instanceof GeneratedPackResources generated && predecessors.contains(generated)
            ? generated.snapshot(snapshots) : baseline.get(source)).toList());
          Map<Identifier, ConcurrentLinkedQueue<ContributionMerger.Submission>> submissions = new ConcurrentHashMap<>();
          var runs = new ArrayList<CompletableFuture<Void>>();
          tasks.forEach(task -> runs.add(invoke(pack, task, phase, snapshot, registries, submissions)));
          CompletableFuture<Void> ordered = CompletableFuture.completedFuture(null);
          for (var rule : rules) {
            ordered = ordered.thenCompose(result -> invoke(pack, rule, phase, snapshot, registries, submissions));
          }
          runs.add(ordered);
          return all(runs).thenRun(() -> {
            if (!closed && !aborted.isDone()) {
              Map<Identifier, GeneratedPackResources.File> files = new HashMap<>();
              submissions.forEach((id, operations) -> files.put(id, ContributionMerger.merge(pack, id, operations, phase, stage, snapshots)));
              pack.publish(files);
            }
          });
        }));
      }
      return all(finished.values()).whenComplete((ignored, failure) -> {
        snapshots.close();
        stageSnapshots.remove(snapshots);
      });
    }

    private CompletableFuture<Void> invoke(GeneratedPackResources pack, Task task, Phase phase, ResourceManager manager,
      HolderLookup.Provider registries, Map<Identifier, ConcurrentLinkedQueue<ContributionMerger.Submission>> submissions) {
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
      var context = new GenerationContext(manager, owner, phase, task.stage(), kind, registries, values, tags, observations, request.queries(), features);
      inputs.add(context);
      Collection<Recorded> recorded = new ConcurrentLinkedQueue<>();
      var output = new GenerationOutput(context, contribution -> submit(pack, task, phase, submissions, recorded, contribution));
      return CompletableFuture.supplyAsync(() -> {
        checkActive();
        String fingerprint = null;
        if (address != null) {
          try {
            fingerprint = CallbackCache.fingerprint(task, context, values, policy, persistent);
          } catch (RuntimeException failure) {
            Constants.LOGGER.warn("Cannot fingerprint callback {} in {}", task.index(), pack.packId(), failure);
          }
        }
        if (address != null && fingerprint != null) {
          var hit = CallbackCache.read(address, fingerprint, observations);
          if (hit != null && replay(pack, task, phase, submissions, recorded, hit)) {
            cacheUpdates.add(new CacheUpdate(address, hit, true, persistent));
            return CompletableFuture.<Void>completedFuture(null);
          }
        }
        try {
          CompletionStage<Void> result = Objects.requireNonNull(task.callback().generate(context, output), "Generation completion stage");
          String proof = fingerprint;
          if (address == null) {
            return result;
          }
          return result.thenRun(() -> {
            CallbackCache.Batch batch = null;
            if (proof != null && observations.validate()) {
              var contributions = recorded.stream().sorted(Comparator.comparingLong(Recorded::sequence)).map(Recorded::contribution).toList();
              batch = new CallbackCache.Batch(UUID.randomUUID().toString(), proof, observations.entries(), contributions);
            } else {
              Constants.LOGGER.debug("Generated without cache for callback {} in {} because an input could not be verified", task.index(), pack.packId());
            }
            cacheUpdates.add(new CacheUpdate(address, batch, false, persistent));
          });
        } catch (Exception failure) {
          return CompletableFuture.<Void>failedFuture(failure);
        }
      }, Util.backgroundExecutor()).thenCompose(CompletionStage::toCompletableFuture).handle((ignored, failure) -> {
        context.close();
        inputs.remove(context);
        if (failure != null) {
          throw new IllegalStateException("Pack " + pack.packId() + " failed during " + phase + "/" + task.stage(), failure);
        }
        return null;
      });
    }

    private void submit(GeneratedPackResources pack, Task task, Phase phase, Map<Identifier, ConcurrentLinkedQueue<ContributionMerger.Submission>> submissions,
      Collection<Recorded> recorded, PackContribution contribution) {
      validateSubmission(pack, task, phase, contribution);
      record(task, submissions, recorded, contribution);
      pack.adoptBatch(List.of(contribution));
    }

    private void record(Task task, Map<Identifier, ConcurrentLinkedQueue<ContributionMerger.Submission>> submissions,
      Collection<Recorded> recorded, PackContribution contribution) {
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

    private boolean replay(GeneratedPackResources pack, Task task, Phase phase, Map<Identifier, ConcurrentLinkedQueue<ContributionMerger.Submission>> submissions,
      Collection<Recorded> recorded, CallbackCache.Batch batch) {
      try {
        batch.values().forEach(contribution -> validateSubmission(pack, task, phase, contribution));
        pack.adoptBatch(batch.values());
      } catch (RuntimeException failure) {
        batch.values().forEach(PackContribution::release);
        Constants.LOGGER.warn("Invalid cached contribution in {}", pack.packId(), failure);
        return false;
      }
      batch.values().forEach(contribution -> record(task, submissions, recorded, contribution));
      return true;
    }

    private void validateSubmission(GeneratedPackResources pack, Task task, Phase phase, PackContribution contribution) {
      Identifier id = contribution.id();
      if (!task.fileRule() && !Objects.requireNonNull(pack.definition).generation().namespaces.contains(id.getNamespace())) {
        throw new IllegalArgumentException("Undeclared resource namespace in " + pack.packId() + ": " + id);
      }
      checkActive();
      // A grouped sidecar inherits the validated file's deadline and lifetime.
      plan.checkOutput(task, phase, id);
    }

  }

  private static CompletableFuture<Void> all(Collection<CompletableFuture<Void>> futures) {
    return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
  }

  private record CacheUpdate(Path address, @Nullable CallbackCache.Batch batch, boolean hit, boolean persistent) {}

  private record Recorded(long sequence, PackContribution contribution) {}

}
