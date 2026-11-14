package it.crystalnest.cobweb.platform.services;

import it.crystalnest.cobweb.api.pack.AssetPack;
import it.crystalnest.cobweb.api.pack.BundledPackResources;
import it.crystalnest.cobweb.api.pack.PackDeclaration;
import it.crystalnest.cobweb.api.pack.ResourcePack;
import it.crystalnest.cobweb.api.pack.model.PackActivation;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
  public abstract BundledPackResources openBundledPack(PackLocationInfo location, String owner, String root);

  /**
   * Returns the existing network namespace for callback-free bundled packs.
   *
   * @return loader-specific known-pack namespace.
   */
  public abstract String bundledKnownPackNamespace();

  /**
   * Associates a pack definition with its optional server asset publication callbacks.
   *
   * @param pack registered pack definition.
   * @param publication server asset publication callbacks, or null if absent.
   */
  public record RegisteredPack(ResourcePack pack, @Nullable PublicationCallbacks<?> publication) {}

  /**
   * Pairs the server-thread capture with the worker callback that consumes its detached inputs.
   *
   * @param capture reads applied server state and returns detached inputs.
   * @param generate writes assets from the detached inputs on a worker.
   * @param <T> detached capture type.
   */
  public record PublicationCallbacks<T>(Function<MinecraftServer, T> capture, AssetPack.PublicationCallback<T> generate) {}
}
