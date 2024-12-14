package it.crystalnest.cobweb.platform;

import it.crystalnest.cobweb.platform.model.Platform;
import it.crystalnest.cobweb.platform.services.PlatformHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.world.flag.FeatureFlagSet;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;

import java.nio.file.Path;
import java.util.List;

/**
 * NeoForge platform helper.
 */
public final class NeoForgePlatformHelper implements PlatformHelper {
  @Override
  public Pack.Metadata createPackMetadata(Component description) {
    return new Pack.Metadata(description, PackCompatibility.COMPATIBLE, FeatureFlagSet.of(), List.of(), true);
  }

  @Override
  public Platform getPlatformName() {
    return Platform.NEOFORGE;
  }

  @Override
  public boolean isModLoaded(String modId) {
    return ModList.get().isLoaded(modId);
  }

  @Override
  public String getModVersion(String modId) {
    return ModList.get().getModContainerById(modId).orElseThrow().getModInfo().getVersion().toString();
  }

  @Override
  public Path getResourcePath(String modId, String path) {
    return ModList.get().getModContainerById(modId).orElseThrow().getModInfo().getOwningFile().getFile().findResource(path);
  }

  @Override
  public boolean isDevEnv() {
    return !FMLLoader.isProduction();
  }
}
