package it.crystalnest.cobweb.platform.services;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Platform specific configuration helper.
 */
public interface ConfigHelper {
  /**
   * Register the configuration for the common side.
   *
   * @param modId mod registering the config.
   * @param spec config specification.
   */
  void registerCommonConfig(String modId, ForgeConfigSpec spec);

  /**
   * Register the configuration for the client side.
   *
   * @param modId mod registering the config.
   * @param spec config specification.
   */
  void registerClientConfig(String modId, ForgeConfigSpec spec);

  /**
   * Register the configuration for the server side.
   *
   * @param modId mod registering the config.
   * @param spec config specification.
   */
  void registerServerConfig(String modId, ForgeConfigSpec spec);
}
