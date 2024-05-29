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

  /**
   * @deprecated Fabric cannot infer the mod ID, use {@link #registerCommonConfig(String, ForgeConfigSpec)} instead.
   */
  @Override
  @Deprecated(since = "0.0.1.0-alpha")
  public void registerCommonConfig(ForgeConfigSpec spec) {
    throwUnsupportedRegistering();
  }

  /**
   * @deprecated Fabric cannot infer the mod ID, use {@link #registerClientConfig(String, ForgeConfigSpec)} instead.
   */
  @Override
  @Deprecated(since = "0.0.1.0-alpha")
  public void registerClientConfig(ForgeConfigSpec spec) {
    throwUnsupportedRegistering();
  }

  /**
   * @deprecated Fabric cannot infer the mod ID, use {@link #registerServerConfig(String, ForgeConfigSpec)} instead.
   */
  @Override
  @Deprecated(since = "0.0.1.0-alpha")
  public void registerServerConfig(ForgeConfigSpec spec) {
    throwUnsupportedRegistering();
  }

  /**
   * Throws an {@link UnsupportedOperationException} for incorrectly registering a configuration on Fabric.
   *
   * @throws UnsupportedOperationException for incorrectly registering a configuration.
   */
  private void throwUnsupportedRegistering() {
    throw new UnsupportedOperationException("Fabric configs must be registered with modId.");
  }
}
