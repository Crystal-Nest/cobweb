package it.crystalnest.cobweb.api.pack.generation.output;

/**
 * Describes which native loads consume a generated resource.
 */
public enum ResourceLifetime {
  /**
   * Generated on full load and retained for the active world during reload.
   */
  FULL_LOAD,
  /**
   * Generated during both initial loading and native reloads.
   */
  RELOADABLE
}
