package it.crystalnest.cobweb.platform.services;

import it.crystalnest.cobweb.api.pack.AssetPack;
import it.crystalnest.cobweb.api.pack.PackActivation;
import it.crystalnest.cobweb.api.pack.ResourcePack;
import it.crystalnest.cobweb.impl.pack.BundledPackResources;
import it.crystalnest.cobweb.impl.pack.PackDeclaration;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Registers Cobweb packs and accesses bundled resources through the current loader.
 */
public abstract class PackHelper {
  /**
   * Registered definitions indexed by type and native ID.
   */
  private final Map<PackType, Map<String, ResourcePack>> packs = new ConcurrentHashMap<>();

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
   * @throws IllegalStateException if the native ID is already registered for this pack type.
   */
  public final synchronized void registerCobwebPack(ResourcePack pack) {
    var byId = packs.computeIfAbsent(pack.type(), type -> new ConcurrentHashMap<>());
    if (byId.containsKey(pack.packId())) {
      throw new IllegalStateException("Pack already registered for " + pack.type() + ": " + pack.identifier());
    }
    addPackSource(pack.type(), pack::toPack);
    byId.put(pack.packId(), pack);
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
    ResourcePack pack = packs.getOrDefault(type, Map.of()).get(id);
    return pack == null ? null : pack.activation();
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
    ResourcePack pack = packs.getOrDefault(type, Map.of()).get(nativeId);
    return pack == null ? null : new PackDeclaration(pack.identifier(), pack.dependencySettings(), pack.loadObserver(), pack.icon(), pack.presentation(), pack.hasExplicitTitle(), pack.hasExplicitDescription());
  }

  /**
   * Resolves an asset pack selected for publication by its logical identifier.
   *
   * @param id logical pack identifier.
   * @return matching registered pack, or null if absent.
   */
  @Nullable
  public final AssetPack assetPack(Identifier id) {
    for (var pack : packs.getOrDefault(PackType.CLIENT_RESOURCES, Map.of()).values()) {
      if (pack instanceof AssetPack asset && asset.identifier().equals(id)) {
        return asset;
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
}
