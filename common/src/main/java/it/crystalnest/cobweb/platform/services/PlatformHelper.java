package it.crystalnest.cobweb.platform.services;

import it.crystalnest.cobweb.platform.model.Environment;
import it.crystalnest.cobweb.platform.model.Platform;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.Pack;

import java.nio.file.Path;
import java.util.NoSuchElementException;

/**
 * Platform specific helper.
 */
public interface PlatformHelper {
  /**
   * Creates a {@link Pack.Metadata}.
   *
   * @param description resource pack description.
   * @return {@link Pack.Metadata}.
   */
  Pack.Metadata createPackMetadata(Component description);

  /**
   * Gets the name of the current platform
   *
   * @return name of the current platform.
   */
  Platform getPlatformName();

  /**
   * Checks if a mod with the given ID is loaded.
   *
   * @param modId mod ID.
   * @return whether the mod is loaded.
   */
  boolean isModLoaded(String modId);

  /**
   * Returns the version of the specified mod.
   *
   * @param modId mod ID.
   * @return mod version.
   * @throws NoSuchElementException if the mod is not loaded.
   */
  String getModVersion(String modId) throws NoSuchElementException;

  /**
   * Returns the {@link Path} for the resource of the specified mod under the given relative path.
   *
   * @param modId mod ID.
   * @param path relative path.
   * @return resource {@link Path}.
   * @throws NoSuchElementException if the mod is not loaded or the path can't be found.
   */
  Path getResourcePath(String modId, String path) throws NoSuchElementException;

  /**
   * Checks if the game is currently in a development environment.
   *
   * @return whether it's a development environment.
   */
  boolean isDevEnv();

  /**
   * Gets the name of the environment type as a string.
   *
   * @return name of the environment type.
   */
  default Environment getEnvironment() {
    return isDevEnv() ? Environment.DEVELOPMENT : Environment.PRODUCTION;
  }
}
