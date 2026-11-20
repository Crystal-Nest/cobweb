package it.crystalnest.cobweb.platform.model;

/**
 * Identifies a supported mod loader and its canonical name.
 */
public enum Platform {
  /**
   * Fabric loader identifier.
   */
  FABRIC("Fabric"),
  /**
   * NeoForge loader identifier.
   */
  NEOFORGE("NeoForge");

  /**
   * Canonical loader name used for display and diagnostics.
   */
  private final String name;

  /**
   * @param name canonical loader name.
   */
  Platform(String name) {
    this.name = name;
  }

  @Override
  public String toString() {
    return name;
  }
}
