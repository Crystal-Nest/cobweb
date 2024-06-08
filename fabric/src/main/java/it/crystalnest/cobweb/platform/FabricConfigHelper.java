package it.crystalnest.cobweb.platform;

import it.crystalnest.cobweb.platform.services.ConfigHelper;
import net.minecraftforge.api.ModLoadingContext;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;

/**
 * Fabric configuration helper.
 */
public final class FabricConfigHelper implements ConfigHelper {
  @Override
  public void registerCommonConfig(String modId, ForgeConfigSpec spec) {
    ModLoadingContext.registerConfig(modId, ModConfig.Type.COMMON, spec);
  }

  @Override
  public void registerClientConfig(String modId, ForgeConfigSpec spec) {
    ModLoadingContext.registerConfig(modId, ModConfig.Type.CLIENT, spec);
  }

  @Override
  public void registerServerConfig(String modId, ForgeConfigSpec spec) {
    ModLoadingContext.registerConfig(modId, ModConfig.Type.SERVER, spec);
  }
}
