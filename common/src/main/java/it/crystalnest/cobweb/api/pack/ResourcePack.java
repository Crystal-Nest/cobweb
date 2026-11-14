package it.crystalnest.cobweb.api.pack;

import it.crystalnest.cobweb.api.pack.dependency.ModRequirement;
import it.crystalnest.cobweb.api.pack.dependency.PackDependencySettings;
import it.crystalnest.cobweb.api.pack.dependency.PackRequirement;
import it.crystalnest.cobweb.api.pack.generation.CallbackRegistration;
import it.crystalnest.cobweb.api.pack.generation.DynamicPackLoads;
import it.crystalnest.cobweb.api.pack.generation.GenerationContext;
import it.crystalnest.cobweb.api.pack.generation.GenerationOutput;
import it.crystalnest.cobweb.api.pack.generation.PackGeneration;
import it.crystalnest.cobweb.api.pack.generation.input.Inputs;
import it.crystalnest.cobweb.api.pack.generation.lifecycle.PackLoadOutcome;
import it.crystalnest.cobweb.api.pack.generation.lifecycle.Phase;
import it.crystalnest.cobweb.api.pack.generation.lifecycle.Stage;
import it.crystalnest.cobweb.api.pack.generation.output.Outputs;
import it.crystalnest.cobweb.api.pack.presentation.PackIcon;
import it.crystalnest.cobweb.api.pack.presentation.PackPresentation;
import it.crystalnest.cobweb.api.pack.presentation.PackPresentationContext;
import it.crystalnest.cobweb.api.pack.query.PackActivation;
import it.crystalnest.cobweb.platform.Services;
import it.crystalnest.cobweb.platform.services.PackHelper;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.util.FileUtil;
import net.minecraft.util.InclusiveRange;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
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
  /**
   * Logical identifier supplied during registration.
   */
  private final Identifier identifier;

  /**
   * Native type of the pack's content.
   */
  private final PackType type;

  /**
   * Base title, source, and ID used in the native repository.
   */
  private final PackLocationInfo location;

  /**
   * Default placement and required selection settings.
   */
  private final PackSelectionConfig selection;

  /**
   * Initial activation policy.
   */
  private final PackActivation activation;

  /**
   * Generation settings and callbacks shared with the internal registration.
   */
  private final PackGeneration generation;

  /**
   * @param registrar configuration for this pack.
   */
  ResourcePack(Registrar<?, ?> registrar) {
    identifier = registrar.identifier;
    FileUtil.decomposePath(identifier.getPath()).getOrThrow();
    type = registrar.type;
    location = new PackLocationInfo("mod/" + identifier.getNamespace() + ":" + registrar.bundledRoot(), registrar.title.copy(), registrar.source, Optional.empty());
    activation = registrar.activation;
    selection = new PackSelectionConfig(activation == PackActivation.ALWAYS_ACTIVE, registrar.position, registrar.fixedPosition);
    generation = new PackGeneration(identifier, type, registrar.namespaces, registrar.after, registrar.before);
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
   * Creates a native {@link Pack} from the registered definition using the same metadata and generation declarations as repository discovery.
   * The conversion does not register or select the resulting pack.
   *
   * @return the native pack, or {@code null} if Minecraft rejects the metadata or the required pack section is missing.
   */
  @Nullable
  public final Pack toPack() {
    return Services.PACK.toPack(this);
  }

  /**
   * Registers a synchronous preparation callback.
   *
   * @param phase explicit phase.
   * @param callback callback executed for applicable native loads.
   * @return callback registration options.
   */
  public final CallbackRegistration onPrepare(Phase phase, Callback callback) {
    return generation.add(phase, Inputs.NONE, Outputs.UNSPECIFIED, Stage.PREPARATION, completed(callback));
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
    return generation.add(phase, Inputs.NONE, outputs, Stage.PREPARATION, completed(callback));
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
    return generation.add(phase, inputs, outputs, Stage.PREPARATION, completed(callback));
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
    return generation.add(null, inputs, outputs, Stage.PREPARATION, completed(callback));
  }

  /**
   * Registers a future-returning preparation callback.
   *
   * @param phase explicit phase.
   * @param callback callback executed for applicable native loads.
   * @return callback registration options.
   */
  public final CallbackRegistration onPrepareAsync(Phase phase, AsyncCallback callback) {
    return generation.add(phase, Inputs.NONE, Outputs.UNSPECIFIED, Stage.PREPARATION, callback);
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
    return generation.add(phase, Inputs.NONE, outputs, Stage.PREPARATION, callback);
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
    return generation.add(phase, inputs, outputs, Stage.PREPARATION, callback);
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
    return generation.add(null, inputs, outputs, Stage.PREPARATION, callback);
  }

  /**
   * Registers a synchronous generation callback.
   *
   * @param phase explicit phase.
   * @param callback callback executed for applicable native loads.
   * @return callback registration options.
   */
  public final CallbackRegistration onGenerate(Phase phase, Callback callback) {
    return generation.add(phase, Inputs.NONE, Outputs.UNSPECIFIED, Stage.GENERATION, completed(callback));
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
    return generation.add(phase, Inputs.NONE, outputs, Stage.GENERATION, completed(callback));
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
    return generation.add(phase, inputs, outputs, Stage.GENERATION, completed(callback));
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
    return generation.add(null, inputs, outputs, Stage.GENERATION, completed(callback));
  }

  /**
   * Registers a future-returning generation callback.
   *
   * @param phase explicit phase.
   * @param callback callback executed for applicable native loads.
   * @return callback registration options.
   */
  public final CallbackRegistration onGenerateAsync(Phase phase, AsyncCallback callback) {
    return generation.add(phase, Inputs.NONE, Outputs.UNSPECIFIED, Stage.GENERATION, callback);
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
    return generation.add(phase, Inputs.NONE, outputs, Stage.GENERATION, callback);
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
    return generation.add(phase, inputs, outputs, Stage.GENERATION, callback);
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
    return generation.add(null, inputs, outputs, Stage.GENERATION, callback);
  }

  /**
   * Registers a synchronous transformation callback.
   *
   * @param phase explicit phase.
   * @param callback callback executed for applicable native loads.
   * @return callback registration options.
   */
  public final CallbackRegistration onTransform(Phase phase, Callback callback) {
    return generation.add(phase, Inputs.NONE, Outputs.UNSPECIFIED, Stage.TRANSFORMATION, completed(callback));
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
    return generation.add(phase, Inputs.NONE, outputs, Stage.TRANSFORMATION, completed(callback));
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
    return generation.add(phase, inputs, outputs, Stage.TRANSFORMATION, completed(callback));
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
    return generation.add(null, inputs, outputs, Stage.TRANSFORMATION, completed(callback));
  }

  /**
   * Registers a future-returning transformation callback.
   *
   * @param phase explicit phase.
   * @param callback callback executed for applicable native loads.
   * @return callback registration options.
   */
  public final CallbackRegistration onTransformAsync(Phase phase, AsyncCallback callback) {
    return generation.add(phase, Inputs.NONE, Outputs.UNSPECIFIED, Stage.TRANSFORMATION, callback);
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
    return generation.add(phase, Inputs.NONE, outputs, Stage.TRANSFORMATION, callback);
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
    return generation.add(phase, inputs, outputs, Stage.TRANSFORMATION, callback);
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
    return generation.add(null, inputs, outputs, Stage.TRANSFORMATION, callback);
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
    return generation.add(null, inputs, outputs, null, completed(callback));
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
    return generation.add(null, inputs, outputs, null, callback);
  }

  /**
   * Adapts a synchronous callback to the common callback contract.
   *
   * @param callback synchronous generation callback.
   * @return callback that completes when generation returns.
   */
  private static AsyncCallback completed(Callback callback) {
    Objects.requireNonNull(callback);
    return (context, output) -> {
      callback.generate(context, output);
      return CompletableFuture.completedFuture(null);
    };
  }

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
    /**
     * Logical identifier of the pack being configured.
     */
    private final Identifier identifier;

    /**
     * Native type of the pack being configured.
     */
    private final PackType type;

    /**
     * Base title used when the native repository discovers the pack.
     */
    private Component title;

    /**
     * Whether Java supplied a base title that takes precedence in the editor.
     */
    private boolean explicitTitle;

    /**
     * Java description override, or {@code null} to inherit file metadata.
     */
    @Nullable
    private Component description;

    /**
     * Java pack format override, or {@code null} to inherit file metadata.
     */
    @Nullable
    private InclusiveRange<PackFormat> formats;

    /**
     * Source that Minecraft attributes to the pack.
     */
    private PackSource source = PackSource.BUILT_IN;

    /**
     * Initial activation policy.
     */
    private PackActivation activation = PackActivation.DEFAULT_DISABLED;

    /**
     * Default placement within the native selection.
     */
    private Pack.Position position = Pack.Position.TOP;

    /**
     * Whether the editor anchors this pack in its native position group.
     */
    private boolean fixedPosition;

    /**
     * Namespaces allowed for Java output.
     */
    private final Set<String> namespaces = new LinkedHashSet<>();

    /**
     * Logical IDs of preferred generation predecessors.
     */
    private final Set<Identifier> after = new LinkedHashSet<>();

    /**
     * Logical IDs of preferred generation successors.
     */
    private final Set<Identifier> before = new LinkedHashSet<>();

    /**
     * Logical pack version, or {@code null} if Java supplies no override.
     */
    @Nullable
    private String version;

    /**
     * Java pack requirements, or {@code null} to inherit the file list.
     */
    @Nullable
    private List<PackRequirement> requiredPacks;

    /**
     * Java mod requirements, or {@code null} to inherit the file list.
     */
    @Nullable
    private List<ModRequirement> requiredMods;

    /**
     * Optional observer of native load outcomes.
     */
    @Nullable
    private Consumer<PackLoadOutcome> observer;

    /**
     * Optional base icon for the selection editor.
     */
    @Nullable
    private PackIcon icon;

    /**
     * Optional callback for conditional presentation in the selection editor.
     */
    @Nullable
    private Function<PackPresentationContext, PackPresentation> presentation;

    /**
     * @param identifier logical pack identifier.
     * @param type native type of the pack's content.
     */
    Registrar(Identifier identifier, PackType type) {
      this.identifier = Objects.requireNonNull(identifier);
      this.type = type;
      title = Component.translatable((type == PackType.SERVER_DATA ? "datapack" : "texturepack") + "." + identifier.getNamespace() + "." + identifier.getPath());
      namespaces.add(identifier.getNamespace());
    }

    /**
     * Returns this registrar with its concrete type.
     *
     * @return this registrar.
     */
    abstract R self();

    /**
     * Resolves the fixed root relative to the owning mod's resources.
     *
     * @return relative path of the bundled pack.
     */
    private String bundledRoot() {
      return (type == PackType.SERVER_DATA ? "datapack/" : "texturepack/") + identifier.getPath();
    }

    /**
     * Sets the pack's title.
     *
     * @param value display title.
     * @return this registrar.
     */
    public final R title(Component value) {
      title = Objects.requireNonNull(value).copy();
      explicitTitle = true;
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

    /**
     * Installs a pack definition during mod initialization.
     *
     * @param pack pack definition to install.
     * @return the registered pack definition.
     * @throws IllegalStateException if registration has closed or the native ID is already registered.
     */
    final P install(P pack) {
      return install(pack, null);
    }

    /**
     * Installs a pack definition with its optional server asset publication callbacks.
     *
     * @param pack pack definition to install.
     * @param publication server asset publication callbacks, or {@code null} if absent.
     * @return the registered pack definition.
     * @throws IllegalStateException if registration has closed or the native ID is already registered.
     */
    final P install(P pack, @Nullable PackHelper.PublicationCallbacks<?> publication) {
      if (DynamicPackLoads.declarationsClosed()) {
        throw new IllegalStateException("Pack registration closed before " + pack.identifier());
      }
      ResourcePack definition = pack;
      var dependencies = new PackDependencySettings(version, requiredPacks, requiredMods);
      var declaration = new PackHelper.PackDeclaration(identifier, dependencies, observer, icon, presentation, explicitTitle, description != null);
      var registration = new PackHelper.RegisteredPack(pack, definition.location, definition.selection, declaration, bundledRoot(), description == null ? null : description.copy(), formats, definition.generation, publication);
      Services.PACK.registerCobwebPack(registration);
      return pack;
    }
  }
}
