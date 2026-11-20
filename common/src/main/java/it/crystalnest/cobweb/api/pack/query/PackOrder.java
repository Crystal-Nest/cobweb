package it.crystalnest.cobweb.api.pack.query;

/**
 * Distinguishes the ordered pack selections.
 */
public enum PackOrder {
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
   * Order proposed by the selection editor.
   */
  EDITED
}
