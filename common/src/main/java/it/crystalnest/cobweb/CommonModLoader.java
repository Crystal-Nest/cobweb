package it.crystalnest.cobweb;

import org.jetbrains.annotations.ApiStatus;

/**
 * Reports Cobweb's own loader setup boundaries.
 */
@ApiStatus.Internal
public final class CommonModLoader {
  /**
   * Shared common setup started milestone.
   */
  static final String COMMON_SETUP_STARTED = "common setup started";

  /**
   * Shared client setup started milestone.
   */
  static final String CLIENT_SETUP_STARTED = "client setup started";

  /**
   * Shared client setup completed milestone.
   */
  static final String CLIENT_SETUP_COMPLETED = "client setup completed";

  private CommonModLoader() {}

  /**
   * Logs a Cobweb setup milestone.
   *
   * @param milestone setup step that started or completed.
   * @param version Cobweb version from the loader.
   * @param loader active mod loader.
   * @param side physical game side.
   */
  public static void logSetup(String milestone, String version, String loader, String side) {
    Constants.LOGGER.debug("Cobweb {} on {} ({}): {}", version, loader, side, milestone);
  }
}
