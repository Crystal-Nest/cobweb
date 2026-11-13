package it.crystalnest.cobweb.api.pack.dependency;

import it.crystalnest.cobweb.platform.Services;

/**
 * Queries mod availability using the active loader.
 */
public final class Mods {
  private Mods() {}

  /**
   * Checks whether the given mod ID is loaded.
   *
   * @param modId loader mod ID.
   * @return whether the mod is installed and loaded.
   */
  public static boolean isLoaded(String modId) {
    return Services.PLATFORM.isModLoaded(modId);
  }
}
