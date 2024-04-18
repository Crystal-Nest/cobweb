package it.crystalnest.cobweb.platform;

import fuzs.forgeconfigapiport.forge.api.neoforge.v4.NeoForgeConfigRegistry;
import it.crystalnest.cobweb.Constants;
import it.crystalnest.cobweb.platform.services.ConfigHelper;
import net.minecraftforge.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class ForgeConfigHelper implements ConfigHelper {
  @Override
  @Deprecated
  public void registerCommonConfig(String modId, ModConfigSpec spec) {
    registerCommonConfig(spec);
    warnUnsupportedRegistering();
  }

  @Override
  @Deprecated
  public void registerClientConfig(String modId, ModConfigSpec spec) {
    registerClientConfig(spec);
    warnUnsupportedRegistering();
  }

  @Override
  @Deprecated
  public void registerServerConfig(String modId, ModConfigSpec spec) {
    registerServerConfig(spec);
    warnUnsupportedRegistering();
  }

  @Override
  public void registerCommonConfig(ModConfigSpec spec) {
    NeoForgeConfigRegistry.INSTANCE.register(ModConfig.Type.COMMON, spec);
  }

  @Override
  public void registerClientConfig(ModConfigSpec spec) {
    NeoForgeConfigRegistry.INSTANCE.register(ModConfig.Type.CLIENT, spec);
  }

  @Override
  public void registerServerConfig(ModConfigSpec spec) {
    NeoForgeConfigRegistry.INSTANCE.register(ModConfig.Type.SERVER, spec);
  }

  private void warnUnsupportedRegistering() {
    Constants.LOGGER.warn("Forge configs should be registered without modId.");
  }
}
