package it.crystalnest.cobweb.api.pack.generation.lifecycle;

/**
 * Identifies the native resource load that is requesting generation.
 */
public enum LoadKind {
  /**
   * Initial loading of the current resource context.
   */
  FULL_LOAD,
  /**
   * Replacement of reloadable resources in the current context.
   */
  RELOAD
}
