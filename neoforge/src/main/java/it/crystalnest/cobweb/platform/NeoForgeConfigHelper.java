package it.crystalnest.cobweb.platform;

import it.crystalnest.cobweb.Constants;
import it.crystalnest.cobweb.platform.services.ConfigHelper;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class NeoForgeConfigHelper implements ConfigHelper {
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
    ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, spec);
  }

  @Override
  public void registerClientConfig(ModConfigSpec spec) {
    ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, spec);
  }

  @Override
  public void registerServerConfig(ModConfigSpec spec) {
    ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, spec);
  }

  private void warnUnsupportedRegistering() {
    Constants.LOGGER.warn("NeoForge configs should be registered without modId.");
  }
}
