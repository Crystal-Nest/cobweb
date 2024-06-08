package it.crystalnest.cobweb.platform;

import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeConfigRegistry;
import it.crystalnest.cobweb.platform.services.ConfigHelper;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Fabric configuration helper.
 */
public final class FabricConfigHelper implements ConfigHelper {
  @Override
  public void registerCommonConfig(String modId, ModConfigSpec spec) {
    NeoForgeConfigRegistry.INSTANCE.register(modId, ModConfig.Type.COMMON, spec);
  }

  @Override
  public void registerClientConfig(String modId, ModConfigSpec spec) {
    NeoForgeConfigRegistry.INSTANCE.register(modId, ModConfig.Type.CLIENT, spec);
  }

  @Override
  public void registerServerConfig(String modId, ModConfigSpec spec) {
    NeoForgeConfigRegistry.INSTANCE.register(modId, ModConfig.Type.SERVER, spec);
  }
}
