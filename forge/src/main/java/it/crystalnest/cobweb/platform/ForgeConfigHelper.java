package it.crystalnest.cobweb.platform;

import it.crystalnest.cobweb.Constants;
import it.crystalnest.cobweb.platform.services.ConfigHelper;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * Forge configuration helper.
 */
public final class ForgeConfigHelper implements ConfigHelper {
  /**
   * @deprecated Forge can infer the mod ID, use {@link #registerCommonConfig(ForgeConfigSpec)} instead.
   */
  @Override
  @Deprecated(since = "0.0.1.0-alpha")
  public void registerCommonConfig(String modId, ForgeConfigSpec spec) {
    registerCommonConfig(spec);
    warnUnsupportedRegistering();
  }

  /**
   * @deprecated Forge can infer the mod ID, use {@link #registerClientConfig(ForgeConfigSpec)} instead.
   */
  @Override
  @Deprecated(since = "0.0.1.0-alpha")
  public void registerClientConfig(String modId, ForgeConfigSpec spec) {
    registerClientConfig(spec);
    warnUnsupportedRegistering();
  }

  /**
   * @deprecated Forge can infer the mod ID, use {@link #registerServerConfig(ForgeConfigSpec)} instead.
   */
  @Override
  @Deprecated(since = "0.0.1.0-alpha")
  public void registerServerConfig(String modId, ForgeConfigSpec spec) {
    registerServerConfig(spec);
    warnUnsupportedRegistering();
  }

  @Override
  public void registerCommonConfig(ForgeConfigSpec spec) {
    ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, spec);
  }

  @Override
  public void registerClientConfig(ForgeConfigSpec spec) {
    ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, spec);
  }

  @Override
  public void registerServerConfig(ForgeConfigSpec spec) {
    ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, spec);
  }

  /**
   * Warns in console for incorrectly registering a configuration on Forge.
   */
  private void warnUnsupportedRegistering() {
    Constants.LOGGER.warn("Forge configs should be registered without modId.");
  }
}
