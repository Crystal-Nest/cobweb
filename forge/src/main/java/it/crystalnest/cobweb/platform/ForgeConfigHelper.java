package it.crystalnest.cobweb.platform;

import fuzs.forgeconfigapiport.forge.api.neoforge.v4.NeoForgeConfigRegistry;
import it.crystalnest.cobweb.platform.services.ConfigHelper;
import net.minecraftforge.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Forge configuration helper.
 */
public final class ForgeConfigHelper implements ConfigHelper {
  @Override
  public void registerCommonConfig(String modId, ModConfigSpec spec) {
    NeoForgeConfigRegistry.INSTANCE.register(ModConfig.Type.COMMON, spec);
  }

  @Override
  public void registerClientConfig(String modId, ModConfigSpec spec) {
    NeoForgeConfigRegistry.INSTANCE.register(ModConfig.Type.CLIENT, spec);
  }

  @Override
  public void registerServerConfig(String modId, ModConfigSpec spec) {
    NeoForgeConfigRegistry.INSTANCE.register(ModConfig.Type.SERVER, spec);
  }
}
