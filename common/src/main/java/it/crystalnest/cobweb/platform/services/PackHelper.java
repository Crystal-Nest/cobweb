package it.crystalnest.cobweb.platform.services;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.crystalnest.cobweb.api.pack.AssetPack;
import it.crystalnest.cobweb.api.pack.ResourcePack;
import it.crystalnest.cobweb.api.pack.dependency.ModRequirement;
import it.crystalnest.cobweb.api.pack.dependency.PackRequirement;
import it.crystalnest.cobweb.api.pack.generation.DynamicPackLoads;
import it.crystalnest.cobweb.api.pack.generation.PackGeneration;
import it.crystalnest.cobweb.api.pack.generation.lifecycle.PackLoadOutcome;
import it.crystalnest.cobweb.api.pack.presentation.PackIcon;
import it.crystalnest.cobweb.api.pack.presentation.PackPresentation;
import it.crystalnest.cobweb.api.pack.presentation.PackPresentationContext;
import it.crystalnest.cobweb.api.pack.query.PackActivation;
import it.crystalnest.cobweb.api.pack.query.PackInfo;
import it.crystalnest.cobweb.platform.Services;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackMetadataResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.util.InclusiveRange;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Registers Cobweb packs and accesses bundled resources through the current loader.
 */
public abstract class PackHelper {
  /**
   * Registrations indexed by type and native ID.
   */
  private final Map<PackType, Map<String, RegisteredPack>> packs = new ConcurrentHashMap<>();

  /**
   * Resolves dependency declarations and pack identities for this service.
   */
  private final NativePackDependencies dependencies = new NativePackDependencies(this);

  /**
   * Reads the pack's own metadata document.
   *
   * @param resources opened primary resources, or {@code null} if absent.
   * @return detached metadata document, or an empty object if the pack has no metadata file.
   * @throws IOException if the file cannot be read.
   */
  public static JsonObject readMetadataFile(@Nullable PackMetadataResources resources) throws IOException {
    var source = resources == null ? null : resources.getRootResource("pack.mcmeta");
    if (source != null) {
      try (var reader = new InputStreamReader(source.get(), StandardCharsets.UTF_8)) {
        return JsonParser.parseReader(reader).getAsJsonObject();
      }
    }
    return new JsonObject();
  }

  /**
   * Adds a source to the native repository for the given pack type.
   *
   * @param type native resource type.
   * @param supplier opens the native pack during discovery.
   */
  protected abstract void addPackSource(PackType type, Supplier<Pack> supplier);

  /**
   * Registers a definition and reserves its native pack identity.
   *
   * @param registration pack handle and its internal settings.
   * @throws IllegalStateException if the native ID is already registered for this pack type.
   */
  public final synchronized void registerCobwebPack(RegisteredPack registration) {
    var pack = registration.pack();
    var byId = packs.computeIfAbsent(pack.type(), type -> new ConcurrentHashMap<>());
    if (byId.containsKey(pack.packId())) {
      throw new IllegalStateException("Pack already registered for " + pack.type() + ": " + pack.identifier());
    }
    addPackSource(pack.type(), () -> toPack(registration));
    byId.put(pack.packId(), registration);
  }

  /**
   * Resolves a public pack handle and creates its native pack.
   *
   * @param pack registered pack handle.
   * @return native pack, or {@code null} if Minecraft rejects the metadata or the required pack section is missing.
   * @throws IllegalStateException if the handle has no matching registration.
   */
  @Nullable
  public final Pack toPack(ResourcePack pack) {
    var registration = packs.getOrDefault(pack.type(), Map.of()).get(pack.packId());
    if (registration == null || registration.pack() != pack) {
      throw new IllegalStateException("Pack is not registered: " + pack.identifier());
    }
    return toPack(registration);
  }

  /**
   * Creates a native pack for repository discovery or server publication.
   *
   * @param registration registered pack whose current generation declarations determine its known-pack identity.
   * @return native pack or {@code null} if its metadata is rejected.
   */
  @Nullable
  public final Pack toPack(RegisteredPack registration) {
    var location = registration.location();
    var pack = registration.pack();
    String owner = pack.identifier().getNamespace();
    if (!registration.generation().generates() && Services.PLATFORM.isModLoaded(owner)) {
      var known = new KnownPack(bundledKnownPackNamespace(), pack.packId(), Services.PLATFORM.getModVersion(owner));
      location = new PackLocationInfo(location.id(), location.title(), location.source(), Optional.of(known));
    }
    return Pack.readMetaAndCreate(location, DynamicPackLoads.resources(registration), pack.type(), registration.selection());
  }

  /**
   * Finds the activation policy of a pack registered through Cobweb.
   *
   * @param type native resource type.
   * @param id native pack ID.
   * @return activation policy or {@code null} for an unmanaged pack.
   */
  @Nullable
  public final PackActivation activation(PackType type, String id) {
    var registration = packs.getOrDefault(type, Map.of()).get(id);
    return registration == null ? null : registration.pack().activation();
  }

  /**
   * Finds the declaration for a registered Cobweb pack.
   *
   * @param type native resource type.
   * @param nativeId ID in the selected native repository.
   * @return pack declaration, or {@code null} for unmanaged packs.
   */
  @Nullable
  public final PackDeclaration packDeclaration(PackType type, String nativeId) {
    var registration = packs.getOrDefault(type, Map.of()).get(nativeId);
    return registration == null ? null : registration.declaration();
  }

  /**
   * Reads the identity of a pack, using the registered logical ID or falling back to file metadata.
   *
   * @param type native resource type.
   * @param nativeId native repository ID.
   * @param resources opened primary metadata resources.
   * @return pack identity with a logical ID when it can be read.
   */
  public final PackInfo packInfo(PackType type, String nativeId, PackMetadataResources resources) {
    return dependencies.info(type, nativeId, resources);
  }

  /**
   * Validates a selection against pack and mod-root declarations and resolves pack requirements for generation ordering.
   *
   * @param type native resource type.
   * @param packs selected packs in increasing priority.
   * @return selected dependency IDs indexed by the requiring native pack ID.
   * @throws IllegalStateException if a requirement is unsatisfied or ambiguous.
   * @throws java.io.UncheckedIOException if dependency metadata cannot be read.
   */
  public final Map<String, List<String>> validateDependencies(PackType type, List<PackResources> packs) {
    return dependencies.validate(type, packs);
  }

  /**
   * Validates the dependencies of the asset packs selected for server delivery.
   *
   * @param packs selected packs in increasing publication priority.
   * @throws IllegalStateException if a requirement is unsatisfied or ambiguous, or a pack requires client mods.
   */
  public final void validatePublicationDependencies(List<PackResources> packs) {
    dependencies.validatePublication(packs);
  }

  /**
   * Writes the registered pack identity and Java dependency settings into exported pack metadata.
   *
   * @param metadata detached metadata object to update.
   * @param declaration registered pack declaration.
   */
  public final void writeDependencyMetadata(JsonObject metadata, PackDeclaration declaration) {
    NativePackDependencies.exportMetadata(metadata, declaration);
  }

  /**
   * Resolves an asset pack selected for publication by its logical identifier.
   *
   * @param id logical pack identifier.
   * @return matching registration, including its publication callbacks if present, or {@code null} if absent.
   */
  @Nullable
  public final RegisteredPack assetPack(Identifier id) {
    for (var registration : packs.getOrDefault(PackType.CLIENT_RESOURCES, Map.of()).values()) {
      if (registration.pack() instanceof AssetPack asset && asset.identifier().equals(id)) {
        return registration;
      }
    }
    return null;
  }

  /**
   * Opens a pack root relative to its owning mod's resource container.
   *
   * @param location native pack identity.
   * @param owner owning mod ID.
   * @param root bundled root relative to the owning mod.
   * @return bundled resource opening, or {@code null} if the owner is absent.
   */
  @Nullable
  public abstract BundledResources openBundledPack(PackLocationInfo location, String owner, String root);

  /**
   * Returns the existing network namespace for callback-free bundled packs.
   *
   * @return loader-specific known-pack namespace.
   */
  public abstract String bundledKnownPackNamespace();

  /**
   * Pairs one open bundled pack root with a traversal used by pack exports and publication.
   * <p>
   * The native resources provide ordinary lookup and metadata. The traversal also includes files at the namespace root.
   * The owner closes the native resources when the opening is no longer in use.
   *
   * @param pack open native resources for the base pack or one overlay.
   * @param listing traversal of all files in one namespace of this opening.
   */
  public record BundledResources(PackResources pack, Listing listing) {
    /**
     * Enumerates all files in one namespace of an open pack, including files directly at its namespace root.
     */
    @FunctionalInterface
    public interface Listing {
      /**
       * Passes each resource identifier and its stream supplier to the receiver.
       *
       * @param type native pack type to traverse.
       * @param namespace namespace whose files to list.
       * @param output receiver of namespaced file identifiers and stream suppliers.
       */
      void list(PackType type, String namespace, PackResources.ResourceOutput output);
    }
  }

  /**
   * Holds the information needed to discover and open a registered pack.
   *
   * @param pack consumer-facing pack handle.
   * @param location native pack identity and base title.
   * @param selection native selection and placement settings.
   * @param declaration logical identity, dependencies, presentation, and observer.
   * @param bundledRoot fixed path relative to the owning mod's resources.
   * @param description Java description override, or {@code null} to inherit file metadata.
   * @param formats Java pack format override, or {@code null} to inherit file metadata.
   * @param generation shared generation settings and live callback declarations.
   * @param publication server asset publication callbacks, or {@code null} if absent.
   */
  public record RegisteredPack(
    ResourcePack pack,
    PackLocationInfo location,
    PackSelectionConfig selection,
    PackDeclaration declaration,
    String bundledRoot,
    @Nullable Component description,
    @Nullable InclusiveRange<PackFormat> formats,
    PackGeneration generation,
    @Nullable PublicationCallbacks<?> publication
  ) {}

  /**
   * Exposes the registered settings used by dependency resolution, presentation, and load notifications.
   * Supplied requirement lists are copied. An explicitly empty list clears the corresponding file list.
   *
   * @param id logical pack identifier.
   * @param dependencyVersion Java pack dependency version, or {@code null} to inherit the file version.
   * @param requiredPacks replacement pack requirements, or {@code null} to inherit the file list.
   * @param requiredMods replacement mod requirements, or {@code null} to inherit the file list.
   * @param observer callback for native load outcomes, or {@code null} if absent.
   * @param icon base icon declared in Java, or {@code null} if absent.
   * @param presentation callback for pack editor presentation, or {@code null} if absent.
   * @param explicitTitle whether Java supplies the base title.
   * @param explicitDescription whether Java supplies the base description.
   */
  public record PackDeclaration(
    Identifier id,
    @Nullable String dependencyVersion,
    @Nullable List<PackRequirement> requiredPacks,
    @Nullable List<ModRequirement> requiredMods,
    @Nullable Consumer<PackLoadOutcome> observer,
    @Nullable PackIcon icon,
    @Nullable Function<PackPresentationContext, PackPresentation> presentation,
    boolean explicitTitle,
    boolean explicitDescription
  ) {
    /**
     * @param id logical pack identifier.
     * @param dependencyVersion Java pack dependency version, or {@code null} to inherit the file version.
     * @param requiredPacks replacement pack requirements, or {@code null} to inherit the file list.
     * @param requiredMods replacement mod requirements, or {@code null} to inherit the file list.
     * @param observer native load outcome callback, or {@code null} if absent.
     * @param icon Java base icon, or {@code null} if absent.
     * @param presentation pack editor presentation callback, or {@code null} if absent.
     * @param explicitTitle whether Java supplies the base title.
     * @param explicitDescription whether Java supplies the base description.
     */
    public PackDeclaration {
      requiredPacks = requiredPacks == null ? null : List.copyOf(requiredPacks);
      requiredMods = requiredMods == null ? null : List.copyOf(requiredMods);
    }
  }

  /**
   * Pairs the server-thread capture with the worker callback that consumes its detached inputs.
   *
   * @param capture reads applied server state and returns detached inputs.
   * @param generate writes assets from the detached inputs on a worker.
   * @param <T> detached capture type.
   */
  public record PublicationCallbacks<T>(Function<MinecraftServer, T> capture, AssetPack.PublicationCallback<T> generate) {}
}
