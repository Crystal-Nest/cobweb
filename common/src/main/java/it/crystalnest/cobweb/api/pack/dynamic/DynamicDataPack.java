package it.crystalnest.cobweb.api.pack.dynamic;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * Dynamic data pack.
 */
public final class DynamicDataPack extends DynamicResourcePack {
  /**
   * List of tag builders.
   */
  private final List<Supplier<DynamicTagBuilder<?>>> tagBuilders = new ArrayList<>();

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
   * Adds into this data pack all the provided {@link DynamicTagBuilder}s.
   *
   * @param builders {@link DynamicTagBuilder}s.
   * @return this {@link DynamicDataPack}.
   */
  public DynamicResourcePack add(DynamicTagBuilder<?>... builders) {
    for (DynamicTagBuilder<?> builder : builders) {
      tagBuilders.add(() -> builder);
    }
    return this;
  }

  /**
   * Adds into this data pack all the provided {@link DynamicTagBuilder}s.
   *
   * @param builders {@link DynamicTagBuilder}s.
   * @return this {@link DynamicDataPack}.
   */
  @SafeVarargs
  public final DynamicResourcePack add(Supplier<DynamicTagBuilder<?>>... builders) {
    tagBuilders.addAll(Arrays.asList(builders));
    return this;
  }

  @Override
  protected void build() {
    for (DynamicTagBuilder<?> builder : tagBuilders.stream().map(Supplier::get).toList()) {
      this.build(builder.getPaths(), builder::json);
    }
  }
}
