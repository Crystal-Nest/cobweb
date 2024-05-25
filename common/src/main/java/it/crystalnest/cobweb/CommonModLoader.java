package it.crystalnest.cobweb;

import it.crystalnest.cobweb.config.ModConfig;

/**
 * Common mod loader.
 */
public final class CommonModLoader {
  private CommonModLoader() {}

  /**
   * Initialize common operations across loaders.
   */
  public static void init() {
    ModConfig.CONFIG.register();
  }
}
