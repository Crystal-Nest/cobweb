package it.crystalnest.cobweb.api.pack.query;

/**
 * Result of a pack status query.
 */
public enum PackStatus {
  /**
   * The requested state contains the pack.
   */
  YES,

  /**
   * The state is available and does not contain the pack.
   */
  NO,

  /**
   * The target state is unavailable.
   */
  UNAVAILABLE
}
