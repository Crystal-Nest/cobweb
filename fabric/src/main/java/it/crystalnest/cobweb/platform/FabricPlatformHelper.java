package it.crystalnest.cobweb.platform;

import it.crystalnest.cobweb.platform.model.Platform;
import it.crystalnest.cobweb.platform.services.PlatformHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.impl.resource.pack.FabricPack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.world.flag.FeatureFlagSet;

import java.nio.file.Path;
import java.util.List;

/**
 * Fabric platform helper.
 */
public final class FabricPlatformHelper implements PlatformHelper {
  /**
   * Checks Fabric's hidden-pack state.
   *
   * @param pack native pack.
   * @return whether Fabric hides the pack from the selection UI.
   */
  @Override
  public boolean isPackHidden(Pack pack) {
    return ((FabricPack) pack).fabric$isHidden();
  }

  @Override
  public Pack.Metadata createPackMetadata(Component description) {
    return new Pack.Metadata(description, PackCompatibility.COMPATIBLE, FeatureFlagSet.of(), List.of());
  }

  @Override
  public Platform getPlatformName() {
    return Platform.FABRIC;
  }

  @Override
  public boolean isModLoaded(String modId) {
    return FabricLoader.getInstance().isModLoaded(modId);
  }

  @Override
  public String getModVersion(String modId) {
    return FabricLoader.getInstance().getModContainer(modId).orElseThrow().getMetadata().getVersion().getFriendlyString();
  }

  @Override
  public Path getResourcePath(String modId, String path) {
    return FabricLoader.getInstance().getModContainer(modId).flatMap(container -> container.findPath(path)).orElseThrow();
  }

  @Override
  public boolean isDevEnv() {
    return FabricLoader.getInstance().isDevelopmentEnvironment();
  }
}
