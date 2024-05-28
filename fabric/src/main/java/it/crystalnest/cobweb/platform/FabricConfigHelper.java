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

  /**
   * @deprecated Fabric cannot infer the mod ID, use {@link #registerCommonConfig(String, ModConfigSpec)} instead.
   */
  @Override
  @Deprecated(since = "1.0.0.0-alpha")
  public void registerCommonConfig(ModConfigSpec spec) {
    throwUnsupportedRegistering();
  }

  /**
   * @deprecated Fabric cannot infer the mod ID, use {@link #registerClientConfig(String, ModConfigSpec)} instead.
   */
  @Override
  @Deprecated(since = "1.0.0.0-alpha")
  public void registerClientConfig(ModConfigSpec spec) {
    throwUnsupportedRegistering();
  }

  /**
   * @deprecated Fabric cannot infer the mod ID, use {@link #registerServerConfig(String, ModConfigSpec)} instead.
   */
  @Override
  @Deprecated(since = "1.0.0.0-alpha")
  public void registerServerConfig(ModConfigSpec spec) {
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
