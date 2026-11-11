package it.crystalnest.cobweb.api.pack;

import it.crystalnest.cobweb.platform.Services;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.util.FileUtil;
import net.minecraft.util.InclusiveRange;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Registered definition of a bundled pack with optional native generation callbacks.
 */
public abstract class ResourcePack {
  private final Identifier identifier;

  private final PackType type;

  private final PackLocationInfo location;

  @Nullable
  private final Component description;

  @Nullable
  private final InclusiveRange<PackFormat> formats;

  private final PackSelectionConfig selection;

  private final PackActivation activation;

  private final Set<String> namespaces;

  private final Set<Identifier> after;

  private final Set<Identifier> before;

  private final PackDependencySettings dependencies;

  @Nullable
  private final Consumer<PackLoadOutcome> observer;

  @Nullable
  private final PackIcon icon;

  @Nullable
  private final Function<PackPresentationContext, PackPresentation> presentation;

  private final List<Task> tasks = new ArrayList<>();

  ResourcePack(Registrar<?, ?> registrar) {
    identifier = registrar.identifier;
    FileUtil.decomposePath(identifier.getPath()).getOrThrow();
    type = registrar.type;
    location = new PackLocationInfo("mod/" + identifier.getNamespace() + ":" + bundledRoot(), registrar.title.copy(), registrar.source, Optional.empty());
    description = registrar.description == null ? null : registrar.description.copy();
    formats = registrar.formats;
    activation = registrar.activation;
    selection = new PackSelectionConfig(activation == PackActivation.ALWAYS_ACTIVE, registrar.position, registrar.fixedPosition);
    namespaces = Set.copyOf(registrar.namespaces);
    after = Set.copyOf(registrar.after);
    before = Set.copyOf(registrar.before);
    dependencies = new PackDependencySettings(registrar.version, registrar.requiredPacks, registrar.requiredMods);
    observer = registrar.observer;
    icon = registrar.icon;
    presentation = registrar.presentation;
  }

  /**
   * Returns the logical identifier of this pack.
   *
   * @return logical pack identifier.
   */
  public final Identifier identifier() {
    return identifier;
  }

  /**
   * Returns the native type of this pack's content.
   *
   * @return resource type.
   */
  public final PackType type() {
    return type;
  }

  /**
   * Returns the activation selected by the registrar.
   *
   * @return activation mode.
   */
  public final PackActivation activation() {
    return activation;
  }

  /**
   * Returns the native ID used in saved selections.
   *
   * @return stable ID shared by bundled and generated content.
   */
  public final String packId() {
    return location.id();
  }

  /**
   * Returns the base title used by native pack discovery.
   *
   * @return detached title component.
   */
  public final Component title() {
    return location.title().copy();
  }

  /**
   * Returns the native pack source.
   *
   * @return pack source.
   */
  public final PackSource source() {
    return location.source();
  }

  /**
   * Returns the pack's default position in native selection.
   *
   * @return top or bottom position.
   */
  public final Pack.Position position() {
    return selection.defaultPosition();
  }

  /**
   * Returns whether this pack is anchored in its native position group.
   *
   * @return whether Minecraft treats its position as fixed.
   */
  public final boolean fixedPosition() {
    return selection.fixedPosition();
  }

  /**
   * Creates the native pack for repository discovery.
   *
   * @return discovered native pack or null if native metadata is rejected.
   */
  @Nullable
  @ApiStatus.Internal
  public final Pack toPack() {
    PackLocationInfo discovered = location;
    String owner = identifier.getNamespace();
    if (!generates() && Services.PLATFORM.isModLoaded(owner)) {
      var known = new KnownPack(Services.REGISTRY.bundledKnownPackNamespace(), packId(), Services.PLATFORM.getModVersion(owner));
      discovered = new PackLocationInfo(location.id(), location.title(), location.source(), Optional.of(known));
    }
    return Pack.readMetaAndCreate(discovered, new GeneratedPackResources.Supplier(this), type, selection);
  }

  /**
   * Registers a synchronous preparation callback.
   *
   * @param phase explicit phase.
   * @param callback callback executed for applicable native loads.
   * @return callback registration options.
   */
  public final CallbackRegistration onPrepare(Phase phase, Callback callback) {
    return add(phase, Inputs.NONE, Outputs.UNSPECIFIED, Stage.PREPARATION, completed(callback));
  }

  /**
   * Registers a synchronous preparation callback.
   *
   * @param phase explicit phase.
   * @param outputs declared output contracts.
   * @param callback callback executed for applicable native loads.
   * @return callback registration options.
   */
  public final CallbackRegistration onPrepare(Phase phase, Outputs outputs, Callback callback) {
    return add(phase, Inputs.NONE, outputs, Stage.PREPARATION, completed(callback));
  }

  /**
   * Registers a synchronous preparation callback.
   *
   * @param phase explicit phase.
   * @param inputs declared input contracts.
   * @param outputs declared output contracts.
   * @param callback callback executed for applicable native loads.
   * @return callback registration options.
   */
  public final CallbackRegistration onPrepare(Phase phase, Inputs inputs, Outputs outputs, Callback callback) {
    return add(phase, inputs, outputs, Stage.PREPARATION, completed(callback));
  }

  /**
   * Registers a synchronous preparation callback.
   *
   * @param inputs declared input contracts.
   * @param outputs declared output contracts.
   * @param callback callback executed for applicable native loads.
   * @return callback registration options.
   */
  public final CallbackRegistration onPrepare(Inputs inputs, Outputs outputs, Callback callback) {
    return add(null, inputs, outputs, Stage.PREPARATION, completed(callback));
  }

  /**
   * Registers a future-returning preparation callback.
   *
   * @param phase explicit phase.
   * @param callback callback executed for applicable native loads.
   * @return callback registration options.
   */
  public final CallbackRegistration onPrepareAsync(Phase phase, AsyncCallback callback) {
    return add(phase, Inputs.NONE, Outputs.UNSPECIFIED, Stage.PREPARATION, callback);
  }

  /**
   * Registers a future-returning preparation callback.
   *
   * @param phase explicit phase.
   * @param outputs declared output contracts.
   * @param callback callback executed for applicable native loads.
   * @return callback registration options.
   */
  public final CallbackRegistration onPrepareAsync(Phase phase, Outputs outputs, AsyncCallback callback) {
    return add(phase, Inputs.NONE, outputs, Stage.PREPARATION, callback);
  }

  /**
   * Registers a future-returning preparation callback.
   *
   * @param phase explicit phase.
   * @param inputs declared input contracts.
   * @param outputs declared output contracts.
   * @param callback callback executed for applicable native loads.
   * @return callback registration options.
   */
  public final CallbackRegistration onPrepareAsync(Phase phase, Inputs inputs, Outputs outputs, AsyncCallback callback) {
    return add(phase, inputs, outputs, Stage.PREPARATION, callback);
  }

  /**
   * Registers a future-returning preparation callback.
   *
   * @param inputs declared input contracts.
   * @param outputs declared output contracts.
   * @param callback callback executed for applicable native loads.
   * @return callback registration options.
   */
  public final CallbackRegistration onPrepareAsync(Inputs inputs, Outputs outputs, AsyncCallback callback) {
    return add(null, inputs, outputs, Stage.PREPARATION, callback);
  }

  /**
   * Registers a synchronous generation callback.
   *
   * @param phase explicit phase.
   * @param callback callback executed for applicable native loads.
   * @return callback registration options.
   */
  public final CallbackRegistration onGenerate(Phase phase, Callback callback) {
    return add(phase, Inputs.NONE, Outputs.UNSPECIFIED, Stage.GENERATION, completed(callback));
  }

  /**
   * Registers a synchronous generation callback.
   *
   * @param phase explicit phase.
   * @param outputs declared output contracts.
   * @param callback callback executed for applicable native loads.
   * @return callback registration options.
   */
  public final CallbackRegistration onGenerate(Phase phase, Outputs outputs, Callback callback) {
    return add(phase, Inputs.NONE, outputs, Stage.GENERATION, completed(callback));
  }

  /**
   * Registers a synchronous generation callback.
   *
   * @param phase explicit phase.
   * @param inputs declared input contracts.
   * @param outputs declared output contracts.
   * @param callback callback executed for applicable native loads.
   * @return callback registration options.
   */
  public final CallbackRegistration onGenerate(Phase phase, Inputs inputs, Outputs outputs, Callback callback) {
    return add(phase, inputs, outputs, Stage.GENERATION, completed(callback));
  }

  /**
   * Registers a synchronous generation callback.
   *
   * @param inputs declared input contracts.
   * @param outputs declared output contracts.
   * @param callback callback executed for applicable native loads.
   * @return callback registration options.
   */
  public final CallbackRegistration onGenerate(Inputs inputs, Outputs outputs, Callback callback) {
    return add(null, inputs, outputs, Stage.GENERATION, completed(callback));
  }

  /**
   * Registers a future-returning generation callback.
   *
   * @param phase explicit phase.
   * @param callback callback executed for applicable native loads.
   * @return callback registration options.
   */
  public final CallbackRegistration onGenerateAsync(Phase phase, AsyncCallback callback) {
    return add(phase, Inputs.NONE, Outputs.UNSPECIFIED, Stage.GENERATION, callback);
  }

  /**
   * Registers a future-returning generation callback.
   *
   * @param phase explicit phase.
   * @param outputs declared output contracts.
   * @param callback callback executed for applicable native loads.
   * @return callback registration options.
   */
  public final CallbackRegistration onGenerateAsync(Phase phase, Outputs outputs, AsyncCallback callback) {
    return add(phase, Inputs.NONE, outputs, Stage.GENERATION, callback);
  }

  /**
   * Registers a future-returning generation callback.
   *
   * @param phase explicit phase.
   * @param inputs declared input contracts.
   * @param outputs declared output contracts.
   * @param callback callback executed for applicable native loads.
   * @return callback registration options.
   */
  public final CallbackRegistration onGenerateAsync(Phase phase, Inputs inputs, Outputs outputs, AsyncCallback callback) {
    return add(phase, inputs, outputs, Stage.GENERATION, callback);
  }

  /**
   * Registers a future-returning generation callback.
   *
   * @param inputs declared input contracts.
   * @param outputs declared output contracts.
   * @param callback callback executed for applicable native loads.
   * @return callback registration options.
   */
  public final CallbackRegistration onGenerateAsync(Inputs inputs, Outputs outputs, AsyncCallback callback) {
    return add(null, inputs, outputs, Stage.GENERATION, callback);
  }

  /**
   * Registers a synchronous transformation callback.
   *
   * @param phase explicit phase.
   * @param callback callback executed for applicable native loads.
   * @return callback registration options.
   */
  public final CallbackRegistration onTransform(Phase phase, Callback callback) {
    return add(phase, Inputs.NONE, Outputs.UNSPECIFIED, Stage.TRANSFORMATION, completed(callback));
  }

  /**
   * Registers a synchronous transformation callback.
   *
   * @param phase explicit phase.
   * @param outputs declared output contracts.
   * @param callback callback executed for applicable native loads.
   * @return callback registration options.
   */
  public final CallbackRegistration onTransform(Phase phase, Outputs outputs, Callback callback) {
    return add(phase, Inputs.NONE, outputs, Stage.TRANSFORMATION, completed(callback));
  }

  /**
   * Registers a synchronous transformation callback.
   *
   * @param phase explicit phase.
   * @param inputs declared input contracts.
   * @param outputs declared output contracts.
   * @param callback callback executed for applicable native loads.
   * @return callback registration options.
   */
  public final CallbackRegistration onTransform(Phase phase, Inputs inputs, Outputs outputs, Callback callback) {
    return add(phase, inputs, outputs, Stage.TRANSFORMATION, completed(callback));
  }

  /**
   * Registers a synchronous transformation callback.
   *
   * @param inputs declared input contracts.
   * @param outputs declared output contracts.
   * @param callback callback executed for applicable native loads.
   * @return callback registration options.
   */
  public final CallbackRegistration onTransform(Inputs inputs, Outputs outputs, Callback callback) {
    return add(null, inputs, outputs, Stage.TRANSFORMATION, completed(callback));
  }

  /**
   * Registers a future-returning transformation callback.
   *
   * @param phase explicit phase.
   * @param callback callback executed for applicable native loads.
   * @return callback registration options.
   */
  public final CallbackRegistration onTransformAsync(Phase phase, AsyncCallback callback) {
    return add(phase, Inputs.NONE, Outputs.UNSPECIFIED, Stage.TRANSFORMATION, callback);
  }

  /**
   * Registers a future-returning transformation callback.
   *
   * @param phase explicit phase.
   * @param outputs declared output contracts.
   * @param callback callback executed for applicable native loads.
   * @return callback registration options.
   */
  public final CallbackRegistration onTransformAsync(Phase phase, Outputs outputs, AsyncCallback callback) {
    return add(phase, Inputs.NONE, outputs, Stage.TRANSFORMATION, callback);
  }

  /**
   * Registers a future-returning transformation callback.
   *
   * @param phase explicit phase.
   * @param inputs declared input contracts.
   * @param outputs declared output contracts.
   * @param callback callback executed for applicable native loads.
   * @return callback registration options.
   */
  public final CallbackRegistration onTransformAsync(Phase phase, Inputs inputs, Outputs outputs, AsyncCallback callback) {
    return add(phase, inputs, outputs, Stage.TRANSFORMATION, callback);
  }

  /**
   * Registers a future-returning transformation callback.
   *
   * @param inputs declared input contracts.
   * @param outputs declared output contracts.
   * @param callback callback executed for applicable native loads.
   * @return callback registration options.
   */
  public final CallbackRegistration onTransformAsync(Inputs inputs, Outputs outputs, AsyncCallback callback) {
    return add(null, inputs, outputs, Stage.TRANSFORMATION, callback);
  }

  /**
   * Registers a callback whose stage and phase follow its declarations.
   *
   * @param inputs declared input contracts.
   * @param outputs declared output contracts.
   * @param callback synchronous generation callback.
   * @return callback registration options.
   */
  public final CallbackRegistration onStage(Inputs inputs, Outputs outputs, Callback callback) {
    return add(null, inputs, outputs, null, completed(callback));
  }

  /**
   * Registers a future-returning callback whose stage and phase follow its declarations.
   *
   * @param inputs declared input contracts.
   * @param outputs declared output contracts.
   * @param callback asynchronous generation callback.
   * @return callback registration options.
   */
  public final CallbackRegistration onStageAsync(Inputs inputs, Outputs outputs, AsyncCallback callback) {
    return add(null, inputs, outputs, null, callback);
  }

  private static AsyncCallback completed(Callback callback) {
    Objects.requireNonNull(callback);
    return (context, output) -> {
      callback.generate(context, output);
      return CompletableFuture.completedFuture(null);
    };
  }

  private synchronized CallbackRegistration add(@Nullable Phase explicit, Inputs inputs, Outputs outputs, @Nullable Stage requestedStage, AsyncCallback callback) {
    if (DynamicPackLoads.declarationsClosed()) {
      throw new IllegalStateException("Pack callback declarations have closed: " + identifier);
    }
    Objects.requireNonNull(callback);
    boolean complete = inputs.values().stream().anyMatch(input -> input instanceof Input.Native nativeInput && nativeInput.completeSource());
    Stage stage = requestedStage == null ? complete ? Stage.TRANSFORMATION : Stage.GENERATION : requestedStage;
    if (complete && stage != Stage.TRANSFORMATION) {
      throw new IllegalArgumentException("Completed ordinary sources require the transformation stage");
    }
    if (explicit == null && outputs.values().isEmpty()) {
      throw new IllegalArgumentException("Generic output requires an explicit phase");
    }
    ResourceLifetime lifetime = outputs.rawLifetime();
    for (Output output : outputs.values()) {
      if (lifetime != null && lifetime != output.lifetime()) {
        throw new IllegalArgumentException("One callback cannot mix full-load and reloadable output");
      }
      lifetime = output.lifetime();
    }
    if (lifetime == null) {
      lifetime = explicit != null && explicit.fullLoadOnly() ? ResourceLifetime.FULL_LOAD : ResourceLifetime.RELOADABLE;
    }
    if (type == PackType.CLIENT_RESOURCES && lifetime == ResourceLifetime.FULL_LOAD) {
      throw new IllegalArgumentException("Client assets must be available on reloads");
    }
    Map<LoadKind, Phase> phases = new EnumMap<>(LoadKind.class);
    for (LoadKind kind : LoadKind.values()) {
      if (kind == LoadKind.RELOAD && lifetime == ResourceLifetime.FULL_LOAD) {
        continue;
      }
      Phase selected = null;
      for (Phase candidate : Phase.values()) {
        if ((explicit == null || candidate == explicit) && compatible(candidate, kind, inputs, outputs)) {
          selected = candidate;
          break;
        }
      }
      if (selected == null) {
        throw new IllegalArgumentException("No compatible " + kind + " phase for " + identifier + " with " + inputs + " and " + outputs);
      }
      phases.put(kind, selected);
    }
    var registration = new CallbackRegistration();
    tasks.add(new Task(Map.copyOf(phases), stage, lifetime, inputs, outputs, callback, registration, tasks.size()));
    return registration;
  }

  private boolean compatible(Phase candidate, LoadKind kind, Inputs inputs, Outputs outputs) {
    if (candidate.type() != type || kind == LoadKind.RELOAD && candidate.fullLoadOnly()) {
      return false;
    }
    for (Input input : inputs.values()) {
      if (input instanceof Input.Native nativeInput) {
        Phase ready = kind == LoadKind.FULL_LOAD ? nativeInput.readiness() : nativeInput.reloadReadiness();
        if (ready.type() != type || candidate.ordinal() < ready.ordinal() || nativeInput.completeSource() && candidate != ready) {
          return false;
        }
      }
    }
    return outputs.values().stream().allMatch(output -> output.deadline().type() == type && candidate.ordinal() <= output.deadline().ordinal());
  }

  synchronized List<Task> tasks() {
    return List.copyOf(tasks);
  }

  synchronized boolean generates() {
    return !tasks.isEmpty();
  }

  Set<String> namespaces() {
    return namespaces;
  }

  @Nullable
  Component description() {
    return description;
  }

  @Nullable
  InclusiveRange<PackFormat> formats() {
    return formats;
  }

  String bundledRoot() {
    return (type == PackType.SERVER_DATA ? "datapack/" : "texturepack/") + identifier.getPath();
  }

  Set<Identifier> after() {
    return after;
  }

  Set<Identifier> before() {
    return before;
  }

  /**
   * Returns explicitly supplied dependency metadata.
   *
   * @return dependency overrides for native loading.
   */
  @ApiStatus.Internal
  public final PackDependencySettings dependencySettings() {
    return dependencies;
  }

  /**
   * Returns the configured native load observer.
   *
   * @return observer, if declared.
   */
  @Nullable
  @ApiStatus.Internal
  public final Consumer<PackLoadOutcome> loadObserver() {
    return observer;
  }

  /**
   * Returns the declared base icon, if any.
   *
   * @return explicit icon, or null to use the native icon.
   */
  @Nullable
  @ApiStatus.Internal
  public final PackIcon icon() {
    return icon;
  }

  /**
   * Returns the pack's display callback.
   *
   * @return display callback, if present.
   */
  @Nullable
  @ApiStatus.Internal
  public final Function<PackPresentationContext, PackPresentation> presentation() {
    return presentation;
  }

  record Task(Map<LoadKind, Phase> phases, Stage stage, ResourceLifetime lifetime, Inputs inputs, Outputs outputs, AsyncCallback callback, CallbackRegistration registration, int index) {}

  /**
   * Generates complete resource submissions before returning.
   */
  @FunctionalInterface
  public interface Callback {
    /**
     * Generates pack content for the current phase.
     *
     * @param context scoped inputs.
     * @param output scoped output writer.
     * @throws Exception if generation fails.
     */
    void generate(GenerationContext context, GenerationOutput output) throws Exception;
  }

  /**
   * Generates complete resource submissions by the time its stage completes.
   */
  @FunctionalInterface
  public interface AsyncCallback {
    /**
     * Starts generation for the current phase.
     *
     * @param context scoped inputs.
     * @param output scoped output writer.
     * @return completion covering all uses of context and output.
     * @throws Exception if generation fails before returning.
     */
    CompletionStage<Void> generate(GenerationContext context, GenerationOutput output) throws Exception;
  }

  /**
   * Configures the fixed attributes of a generated pack.
   *
   * @param <P> registered pack type.
   * @param <R> registrar type.
   */
  public abstract static class Registrar<P extends ResourcePack, R extends Registrar<P, R>> {
    private final Identifier identifier;

    private final PackType type;

    private Component title;

    @Nullable
    private Component description;

    @Nullable
    private InclusiveRange<PackFormat> formats;

    private PackSource source = PackSource.BUILT_IN;

    private PackActivation activation = PackActivation.DEFAULT_DISABLED;

    private Pack.Position position = Pack.Position.TOP;

    private boolean fixedPosition;

    private final Set<String> namespaces = new LinkedHashSet<>();

    private final Set<Identifier> after = new LinkedHashSet<>();

    private final Set<Identifier> before = new LinkedHashSet<>();

    @Nullable
    private String version;

    @Nullable
    private List<PackRequirement> requiredPacks;

    @Nullable
    private List<ModRequirement> requiredMods;

    @Nullable
    private Consumer<PackLoadOutcome> observer;

    @Nullable
    private PackIcon icon;

    @Nullable
    private Function<PackPresentationContext, PackPresentation> presentation;

    Registrar(Identifier identifier, PackType type) {
      this.identifier = Objects.requireNonNull(identifier);
      this.type = type;
      title = Component.translatable((type == PackType.SERVER_DATA ? "datapack" : "texturepack") + "." + identifier.getNamespace() + "." + identifier.getPath());
      namespaces.add(identifier.getNamespace());
    }

    abstract R self();

    /**
     * Sets the pack's title.
     *
     * @param value display title.
     * @return this registrar.
     */
    public final R title(Component value) {
      title = Objects.requireNonNull(value).copy();
      return self();
    }

    /**
     * Sets a translatable pack title.
     *
     * @param translationKey title translation key.
     * @return this registrar.
     */
    public final R title(String translationKey) {
      return title(Component.translatable(translationKey));
    }

    /**
     * Sets the native pack description.
     *
     * @param value display description.
     * @return this registrar.
     */
    public final R description(Component value) {
      description = Objects.requireNonNull(value).copy();
      return self();
    }

    /**
     * Sets supported native pack formats.
     *
     * @param value inclusive range of supported formats.
     * @return this registrar.
     */
    public final R formats(InclusiveRange<PackFormat> value) {
      formats = Objects.requireNonNull(value);
      return self();
    }

    /**
     * Declares compatibility with this game's current native pack format.
     *
     * @return this registrar.
     */
    public final R currentFormat() {
      PackFormat format = SharedConstants.getCurrentVersion().packVersion(type);
      return formats(new InclusiveRange<>(format, format));
    }

    /**
     * Sets the pack's native source label.
     *
     * @param value native pack source.
     * @return this registrar.
     */
    public final R source(PackSource value) {
      source = Objects.requireNonNull(value);
      return self();
    }

    /**
     * Sets the initial or required pack activation.
     *
     * @param value activation mode.
     * @return this registrar.
     */
    public final R activation(PackActivation value) {
      activation = Objects.requireNonNull(value);
      return self();
    }

    /**
     * Sets the initial insertion side.
     *
     * @param value pack position.
     * @return this registrar.
     */
    public final R position(Pack.Position value) {
      position = Objects.requireNonNull(value);
      return self();
    }

    /**
     * Sets whether the native selection UI anchors the pack position.
     *
     * @param value whether to fix the position.
     * @return this registrar.
     */
    public final R fixedPosition(boolean value) {
      fixedPosition = value;
      return self();
    }

    /**
     * Declares additional generated resource namespaces before native routing.
     *
     * @param values additional resource namespaces.
     * @return this registrar.
     */
    public final R namespaces(String... values) {
      for (String namespace : values) {
        namespaces.add(Identifier.fromNamespaceAndPath(namespace, "validation").getNamespace());
      }
      return self();
    }

    /**
     * Orders this pack after the listed dynamic packs when they participate.
     *
     * @param packs same-type dynamic pack identifiers.
     * @return this registrar.
     */
    public final R after(Identifier... packs) {
      after.addAll(List.of(packs));
      return self();
    }

    /**
     * Orders this pack before the listed dynamic packs when they participate.
     *
     * @param packs same-type dynamic pack identifiers.
     * @return this registrar.
     */
    public final R before(Identifier... packs) {
      before.addAll(List.of(packs));
      return self();
    }

    /**
     * Replaces the list of required same-type packs, including file declarations. An empty list clears it.
     *
     * @param packs required pack references and optional version constraints.
     * @return this registrar.
     */
    public final R requiredPacks(PackRequirement... packs) {
      requiredPacks = List.of(packs);
      return self();
    }

    /**
     * Replaces the list of required installed mods. An empty list clears it.
     *
     * @param mods mod requirements.
     * @return this registrar.
     */
    public final R requiredMods(ModRequirement... mods) {
      requiredMods = List.of(mods);
      return self();
    }

    /**
     * Sets the version exposed to pack dependencies.
     *
     * @param version logical pack version.
     * @return this registrar.
     */
    public final R version(String version) {
      this.version = Objects.requireNonNull(version);
      return self();
    }

    /**
     * Observes the native outcome when this pack participates in a resource load.
     *
     * @param observer detached outcome consumer.
     * @return this registrar.
     */
    public final R onLoadCompleted(Consumer<PackLoadOutcome> observer) {
      this.observer = Objects.requireNonNull(observer);
      return self();
    }

    /**
     * Sets the base icon displayed for this pack in the selection screen.
     *
     * @param value explicit icon origin and path.
     * @return this registrar.
     */
    public final R icon(PackIcon value) {
      icon = Objects.requireNonNull(value);
      return self();
    }

    /**
     * Supplies partial display changes when the selection screen refreshes.
     *
     * @param callback presentation callback, evaluated with the editor's current observations.
     * @return this registrar.
     */
    public final R presentation(Function<PackPresentationContext, PackPresentation> callback) {
      presentation = Objects.requireNonNull(callback);
      return self();
    }

    /**
     * Creates and registers the pack during mod initialization.
     *
     * @return the registered pack definition.
     */
    public abstract P register();

    final P install(P pack) {
      if (DynamicPackLoads.declarationsClosed()) {
        throw new IllegalStateException("Pack registration closed before " + pack.identifier());
      }
      Services.REGISTRY.registerCobwebPack(pack);
      return pack;
    }
  }
}
