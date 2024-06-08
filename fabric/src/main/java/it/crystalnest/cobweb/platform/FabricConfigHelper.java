package it.crystalnest.cobweb.platform;

import fuzs.forgeconfigapiport.api.config.v2.ForgeConfigRegistry;
import it.crystalnest.cobweb.platform.services.ConfigHelper;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;

/**
 * Fabric configuration helper.
 */
public final class FabricConfigHelper implements ConfigHelper {
  @Override
  public void registerCommonConfig(String modId, ForgeConfigSpec spec) {
    ForgeConfigRegistry.INSTANCE.register(modId, ModConfig.Type.COMMON, spec);
  }

  @Override
  public void registerClientConfig(String modId, ForgeConfigSpec spec) {
    ForgeConfigRegistry.INSTANCE.register(modId, ModConfig.Type.CLIENT, spec);
  }

  @Override
  public void registerServerConfig(String modId, ForgeConfigSpec spec) {
    ForgeConfigRegistry.INSTANCE.register(modId, ModConfig.Type.SERVER, spec);
  }
}
