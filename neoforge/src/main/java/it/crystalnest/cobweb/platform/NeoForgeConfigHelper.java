package it.crystalnest.cobweb.platform;

import it.crystalnest.cobweb.platform.services.ConfigHelper;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * NeoForge configuration helper.
 */
public final class NeoForgeConfigHelper implements ConfigHelper {
  /**
   * NeoForge mod container.
   */
  private ModContainer container = null;

  @Override
  public void registerCommonConfig(String modId, ModConfigSpec spec) {
    container.registerConfig(ModConfig.Type.COMMON, spec);
  }

  @Override
  public void registerClientConfig(String modId, ModConfigSpec spec) {
    container.registerConfig(ModConfig.Type.CLIENT, spec);
  }

  @Override
  public void registerServerConfig(String modId, ModConfigSpec spec) {
    container.registerConfig(ModConfig.Type.SERVER, spec);
  }

  /**
   * Saves the given container to register configurations.
   *
   * @param container {@link ModContainer}.
   */
  public void register(ModContainer container) {
    this.container = container;
  }
}
