package it.crystalnest.cobweb.api.config;

/**
 * Configuration type.
 */
public enum ConfigType {
  /**
   * Server configuration type.
   */
  SERVER,
  /**
   * Client configuration type.
   */
  CLIENT,
  /**
   * Common configuration type.
   */
  COMMON;

  @Override
  public String toString() {
    return this.name().toLowerCase();
  }
}
