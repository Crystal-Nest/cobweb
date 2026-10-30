package it.crystalnest.cobweb.api.pack;

/**
 * Activation of a bundled pack; optional defaults never replace a saved user choice.
 */
public enum PackActivation {
  /**
   * Optional and initially disabled.
   */
  DEFAULT_DISABLED,

  /**
   * Optional and initially enabled.
   */
  DEFAULT_ENABLED,

  /**
   * Required, independently of whether its position is movable.
   */
  ALWAYS_ACTIVE
}
