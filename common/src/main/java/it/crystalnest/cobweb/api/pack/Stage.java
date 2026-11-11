package it.crystalnest.cobweb.api.pack;

/**
 * Describes the current stage of a native generation phase.
 */
public enum Stage {
  /**
   * Prepares output before ordinary generation.
   */
  PREPARATION,

  /**
   * Produces ordinary output from the completed preparation stage.
   */
  GENERATION,

  /**
   * Transforms complete source definitions after ordinary generation.
   */
  TRANSFORMATION
}
