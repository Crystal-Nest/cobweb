package it.crystalnest.cobweb.api.pack.query;

/**
 * Distinguishes pack status observations.
 */
public enum PackQuestion {
  /**
   * Discovered in the repository.
   */
  AVAILABLE,

  /**
   * Part of the repository selection.
   */
  SELECTED,

  /**
   * Part of the current native request.
   */
  REQUESTED,

  /**
   * Part of the latest successful native load.
   */
  APPLIED,

  /**
   * Part of the editor's proposed selection.
   */
  EDITED_SELECTED
}
