package it.crystalnest.cobweb.api.pack;

import net.minecraft.resources.ResourceLocation;

/**
 * Available resource types for a dynamic resource pack.
 */
public enum DynamicResourceType {
  /**
   * General.
   */
  GENERAL("%s"),
  /**
   * Tag.
   */
  TAG("tags/%s.json");

  /**
   * Path pattern for this resource type.
   */
  private final String path;

  /**
   * @param path {@link #path}.
   */
  DynamicResourceType(String path) {
    this.path = path;
  }

  /**
   * Returns the complete path for this resource type.
   *
   * @param id resource ID.
   * @return complete path for this resource.
   */
  public ResourceLocation getPath(ResourceLocation id) {
    return new ResourceLocation(id.getNamespace(), String.format(path, id.getPath()));
  }
}
