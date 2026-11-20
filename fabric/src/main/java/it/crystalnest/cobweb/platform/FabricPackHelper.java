package it.crystalnest.cobweb.platform;

import it.crystalnest.cobweb.Constants;
import it.crystalnest.cobweb.api.pack.ResourcePack;
import it.crystalnest.cobweb.api.pack.PackSources;
import it.crystalnest.cobweb.platform.services.PackHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Adds Cobweb packs to Fabric repositories and opens packs bundled with Fabric mods.
 */
public final class FabricPackHelper extends PackHelper {
  /**
   * Internal list of {@link Pack data pack}s to register.
   */
  private static final List<Supplier<Pack>> DATA_PACKS = new ArrayList<>();

  /**
   * Internal list of {@link Pack texture pack}s to register.
   */
  private static final List<Supplier<Pack>> ASSET_PACKS = new ArrayList<>();

  /**
   * Registers all Cobweb data packs with the given repository.
   *
   * @param repository {@link PackRepository}.
   * @return updated repository.
   */
  public static PackRepository registerDataPacks(PackRepository repository) {
    registerPacks(DATA_PACKS, repository);
    return repository;
  }

  /**
   * Registers all Cobweb asset packs with the given repository.
   *
   * @param repository {@link PackRepository}.
   * @return updated repository.
   */
  public static PackRepository registerTexturePacks(PackRepository repository) {
    registerPacks(ASSET_PACKS, repository);
    return repository;
  }

  /**
   * Registers the provided list of resource packs to the given repository.
   *
   * @param packs resource packs.
   * @param repository {@link PackRepository}.
   */
  private static void registerPacks(List<Supplier<Pack>> packs, PackRepository repository) {
    for (var definition : packs) {
      ((PackSources) repository).addSource(consumer -> {
        Pack pack = definition.get();
        if (pack != null) {
          consumer.accept(pack);
        }
      });
    }
  }

  @Override
  protected void addPackSource(ResourcePack pack, Supplier<Pack> supplier) {
    PackType type = pack.type();
    switch (type) {
      case SERVER_DATA -> DATA_PACKS.add(supplier);
      case CLIENT_RESOURCES -> ASSET_PACKS.add(supplier);
    }
  }

  @Override
  public String bundledKnownPackNamespace() {
    return Constants.MOD_ID;
  }

  @Override
  @Nullable
  public BundledResources openBundledPack(PackLocationInfo location, String owner, String root) {
    return FabricLoader.getInstance().getModContainer(owner).map(container -> {
      var path = container.findPath(root).orElseGet(() -> container.getRootPaths().getFirst().resolve(root));
      return new BundledResources(new PathPackResources(location, path), (type, namespace, output) -> PathPackResources.listPath(namespace, path.resolve(type.getDirectory()).resolve(namespace), List.of(), output));
    }).orElse(null);
  }
}
