package it.crystalnest.cobweb.api.pack;

import net.minecraft.server.packs.PackType;

/**
 * A native resource-loading window with distinct input readiness and output deadlines.
 */
public enum Phase {
  /**
   * Data resources before native tag preparation, on full loads and reloads.
   */
  DATA_RESOURCES(PackType.SERVER_DATA, false),

  /**
   * Prepared static tags before world registry decoding, on full loads.
   */
  STATIC_TAGS_READY(PackType.SERVER_DATA, true),

  /**
   * Loaded world registries before dimension decoding, on full loads.
   */
  WORLD_REGISTRIES_READY(PackType.SERVER_DATA, true),

  /**
   * World and dimension context before reloadable registries, on full loads and reloads.
   */
  WORLD_CONTEXT_READY(PackType.SERVER_DATA, false),

  /**
   * Rebuilt reloadable registries before ordinary listeners, on full loads and reloads.
   */
  RELOADABLE_REGISTRIES_READY(PackType.SERVER_DATA, false),

  /**
   * Client assets before native listener preparation, on initial loading and reloads.
   */
  CLIENT_RESOURCES(PackType.CLIENT_RESOURCES, false);

  private final PackType type;

  private final boolean fullLoadOnly;

  Phase(PackType type, boolean fullLoadOnly) {
    this.type = type;
    this.fullLoadOnly = fullLoadOnly;
  }

  /**
   * Returns the resource type for this phase.
   *
   * @return native resource type.
   */
  public PackType type() {
    return type;
  }

  /**
   * Indicates whether this phase occurs only during a full load.
   *
   * @return whether the phase is exclusive to full loads.
   */
  public boolean fullLoadOnly() {
    return fullLoadOnly;
  }
}
