package it.crystalnest.cobweb.platform.model;

/**
 * Platform.
 */
public enum Platform {
  /**
   * Fabric loader identifier.
   */
  FABRIC,
  /**
   * Forge loader identifier.
   */
  FORGE;

  @Override
  public String toString() {
    return this.name().toLowerCase();
  }
}
