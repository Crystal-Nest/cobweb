package it.crystalnest.cobweb.platform;

import it.crystalnest.cobweb.Constants;
import it.crystalnest.cobweb.platform.services.ConfigHelper;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * NeoForge configuration helper.
 */
public final class NeoForgeConfigHelper implements ConfigHelper {
  /**
   * @deprecated Forge can infer the mod ID, use {@link #registerCommonConfig(ModConfigSpec)} instead.
   */
  @Override
  @Deprecated(since = "1.0.0.0-alpha")
  public void registerCommonConfig(String modId, ModConfigSpec spec) {
    registerCommonConfig(spec);
    warnUnsupportedRegistering();
  }

  /**
   * @deprecated Forge can infer the mod ID, use {@link #registerClientConfig(ModConfigSpec)} instead.
   */
  @Override
  @Deprecated(since = "1.0.0.0-alpha")
  public void registerClientConfig(String modId, ModConfigSpec spec) {
    registerClientConfig(spec);
    warnUnsupportedRegistering();
  }

  /**
   * @deprecated Forge can infer the mod ID, use {@link #registerServerConfig(ModConfigSpec)} instead.
   */
  @Override
  @Deprecated(since = "1.0.0.0-alpha")
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

  /**
   * Warns in console for incorrectly registering a configuration on NeoForge.
   */
  private void warnUnsupportedRegistering() {
    Constants.LOGGER.warn("NeoForge configs should be registered without modId.");
  }
}
