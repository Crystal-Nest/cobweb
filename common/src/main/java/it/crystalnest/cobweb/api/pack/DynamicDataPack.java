package it.crystalnest.cobweb.api.pack;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;

/**
 * Dynamic data pack.
 */
public class DynamicDataPack extends DynamicResourcePack {
  /**
   * @param name data pack name.
   */
  private DynamicDataPack(ResourceLocation name) {
    super(name, PackType.SERVER_DATA);
  }

  /**
   * Creates a new {@link DynamicDataPack} with the provided name.
   *
   * @param name data pack name.
   * @return a new {@link DynamicDataPack}.
   */
  public static DynamicDataPack named(ResourceLocation name) {
    return new DynamicDataPack(name);
  }

  /**
   * Builds into this data pack all the provided {@link DynamicTagBuilder}s.
   *
   * @param builders {@link DynamicTagBuilder}s.
   * @return this {@link DynamicDataPack}.
   */
  public DynamicResourcePack build(DynamicTagBuilder<?>... builders) {
    for (DynamicTagBuilder<?> builder : builders) {
      this.build(builder.getPaths(), builder::json);
    }
    return this;
  }
}
