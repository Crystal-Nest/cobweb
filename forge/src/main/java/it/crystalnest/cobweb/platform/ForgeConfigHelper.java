package it.crystalnest.cobweb.platform;

import it.crystalnest.cobweb.platform.services.ConfigHelper;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * Forge configuration helper.
 */
public final class ForgeConfigHelper implements ConfigHelper {
  @Override
  public void registerCommonConfig(String modId, ForgeConfigSpec spec) {
    ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, spec);
  }

  @Override
  public void registerClientConfig(String modId, ForgeConfigSpec spec) {
    ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, spec);
  }

  @Override
  public void registerServerConfig(String modId, ForgeConfigSpec spec) {
    ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, spec);
  }
}
