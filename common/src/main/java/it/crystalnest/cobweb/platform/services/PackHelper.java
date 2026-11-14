package it.crystalnest.cobweb.platform.services;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.crystalnest.cobweb.api.pack.AssetPack;
import it.crystalnest.cobweb.api.pack.ResourcePack;
import it.crystalnest.cobweb.api.pack.dependency.PackDependencySettings;
import it.crystalnest.cobweb.api.pack.generation.lifecycle.PackLoadOutcome;
import it.crystalnest.cobweb.api.pack.model.PackActivation;
import it.crystalnest.cobweb.api.pack.presentation.PackIcon;
import it.crystalnest.cobweb.api.pack.presentation.PackPresentation;
import it.crystalnest.cobweb.api.pack.presentation.PackPresentationContext;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackMetadataResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
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
   * Reads the pack's own metadata document.
   *
   * @param resources opened primary resources, or null if absent.
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
   * @param pack pack definition to register.
   * @param publication server asset publication callbacks, or null if absent.
   * @throws IllegalStateException if the native ID is already registered for this pack type.
   */
  public final synchronized void registerCobwebPack(ResourcePack pack, @Nullable PublicationCallbacks<?> publication) {
    var byId = packs.computeIfAbsent(pack.type(), type -> new ConcurrentHashMap<>());
    if (byId.containsKey(pack.packId())) {
      throw new IllegalStateException("Pack already registered for " + pack.type() + ": " + pack.identifier());
    }
    addPackSource(pack.type(), pack::toPack);
    byId.put(pack.packId(), new RegisteredPack(pack, publication));
  }

  /**
   * Finds the activation policy of a pack registered through Cobweb.
   *
   * @param type native resource type.
   * @param id native pack ID.
   * @return activation policy or null for an unmanaged pack.
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
   * @return pack declaration, or null for unmanaged packs.
   */
  @Nullable
  public final PackDeclaration packDeclaration(PackType type, String nativeId) {
    var registration = packs.getOrDefault(type, Map.of()).get(nativeId);
    if (registration == null) {
      return null;
    }
    var pack = registration.pack();
    return new PackDeclaration(pack.identifier(), pack.dependencySettings(), pack.loadObserver(), pack.icon(), pack.presentation(), pack.hasExplicitTitle(), pack.hasExplicitDescription());
  }

  /**
   * Resolves an asset pack selected for publication by its logical identifier.
   *
   * @param id logical pack identifier.
   * @return matching registration, including its publication callbacks if present, or null if absent.
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
   * @return bundled resource opening, or null if the owner is absent.
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
   * Associates a pack definition with its optional server asset publication callbacks.
   *
   * @param pack registered pack definition.
   * @param publication server asset publication callbacks, or null if absent.
   */
  public record RegisteredPack(ResourcePack pack, @Nullable PublicationCallbacks<?> publication) {}

  /**
   * Exposes the registered settings used by dependency resolution, presentation, and load notifications.
   *
   * @param id logical pack identifier.
   * @param dependencies dependency settings declared in Java.
   * @param observer callback for native load outcomes, or null if absent.
   * @param icon base icon declared in Java, or null if absent.
   * @param presentation callback for pack editor presentation, or null if absent.
   * @param explicitTitle whether Java supplies the base title.
   * @param explicitDescription whether Java supplies the base description.
   */
  public record PackDeclaration(
    Identifier id,
    PackDependencySettings dependencies,
    @Nullable Consumer<PackLoadOutcome> observer,
    @Nullable PackIcon icon,
    @Nullable Function<PackPresentationContext, PackPresentation> presentation,
    boolean explicitTitle,
    boolean explicitDescription
  ) {}

  /**
   * Pairs the server-thread capture with the worker callback that consumes its detached inputs.
   *
   * @param capture reads applied server state and returns detached inputs.
   * @param generate writes assets from the detached inputs on a worker.
   * @param <T> detached capture type.
   */
  public record PublicationCallbacks<T>(Function<MinecraftServer, T> capture, AssetPack.PublicationCallback<T> generate) {}
}
