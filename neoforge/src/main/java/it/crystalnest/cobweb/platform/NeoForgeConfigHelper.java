package it.crystalnest.cobweb.platform;

import it.crystalnest.cobweb.platform.services.ConfigHelper;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * NeoForge configuration helper.
 */
public final class NeoForgeConfigHelper implements ConfigHelper {
  @Override
  public void registerCommonConfig(String modId, ModConfigSpec spec) {
    ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.COMMON, spec);
  }

  @Override
  public void registerClientConfig(String modId, ModConfigSpec spec) {
    ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.CLIENT, spec);
  }

  @Override
  public void registerServerConfig(String modId, ModConfigSpec spec) {
    ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.SERVER, spec);
  }
}
