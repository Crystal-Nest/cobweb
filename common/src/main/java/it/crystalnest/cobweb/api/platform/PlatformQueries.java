package it.crystalnest.cobweb.api.platform;

import it.crystalnest.cobweb.platform.Services;
import it.crystalnest.cobweb.platform.model.Environment;
import it.crystalnest.cobweb.platform.model.Platform;

import java.util.List;
import java.util.Optional;

/**
 * Queries the active loader, launch environment, and loaded mods in the local game instance.
 * Mod queries are available once the loader makes its mod list available during initialization.
 */
public final class PlatformQueries {
  private PlatformQueries() {}

  /**
   * Returns the active mod loader.
   *
   * @return loader for this game instance.
   */
  public static Platform loader() {
    return Services.PLATFORM.getPlatformName();
  }

  /**
   * Returns whether the game was launched in a development or production environment.
   *
   * @return launch environment reported by the active loader.
   */
  public static Environment environment() {
    return Services.PLATFORM.getEnvironment();
  }

  /**
   * Checks whether the game was launched in a development environment.
   *
   * @return whether the active loader reports a development environment.
   */
  public static boolean isDevelopment() {
    return Services.PLATFORM.isDevEnv();
  }

  /**
   * Checks whether the active loader reports a mod as loaded.
   *
   * @param modId mod identifier used by the loader.
   * @return whether the mod is loaded in the local game instance.
   */
  public static boolean isModLoaded(String modId) {
    return Services.PLATFORM.isModLoaded(modId);
  }

  /**
   * Returns the mod IDs reported by the active loader.
   *
   * @return unmodifiable list of mod IDs in ascending lexicographic order.
   */
  public static List<String> loadedModIds() {
    return Services.PLATFORM.loadedModIds();
  }

  /**
   * Returns the version string reported by the active loader for a loaded mod.
   *
   * @param modId mod identifier used by the loader.
   * @return loader version string, or an empty optional if the mod is absent.
   */
  public static Optional<String> modVersion(String modId) {
    return isModLoaded(modId) ? Optional.of(Services.PLATFORM.getModVersion(modId)) : Optional.empty();
  }
}
