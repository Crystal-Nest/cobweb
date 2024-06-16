package it.crystalnest.cobweb.platform.services;

import it.crystalnest.cobweb.platform.model.Environment;
import it.crystalnest.cobweb.platform.model.Platform;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.Pack;

/**
 * Platform specific helper.
 */
public interface PlatformHelper {
  /**
   * Creates a {@link Pack.Info}.
   *
   * @param description resource pack description.
   * @param packFormat pack format.
   * @return {@link Pack.Info}.
   */
  Pack.Info createPackInfo(Component description, int packFormat);

  /**
   * Gets the name of the current platform
   *
   * @return The name of the current platform.
   */
  Platform getPlatformName();

  /**
   * Checks if a mod with the given id is loaded.
   *
   * @param modId The mod to check if it is loaded.
   * @return True if the mod is loaded, false otherwise.
   */
  boolean isModLoaded(String modId);

  /**
   * Check if the game is currently in a development environment.
   *
   * @return True if in a development environment, false otherwise.
   */
  boolean isDevEnv();

  /**
   * Gets the name of the environment type as a string.
   *
   * @return The name of the environment type.
   */
  default Environment getEnvironment() {
    return isDevEnv() ? Environment.DEVELOPMENT : Environment.PRODUCTION;
  }
}
