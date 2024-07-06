package it.crystalnest.cobweb.api.pack;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagBuilder;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagFile;
import net.minecraft.tags.TagKey;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Builder for dynamic tags.
 *
 * @param <T>
 */
@SuppressWarnings("unchecked")
public class DynamicTagBuilder<T> extends TagBuilder {
  /**
   * Set of unique keys.
   */
  private final Set<String> keys = new HashSet<>();

  /**
   * Tags to build.
   */
  private final List<ResourceLocation> tags;

  /**
   * {@link Registry} reference.
   */
  private final Registry<T> registry;

  /**
   * @param registry {@link #registry}.
   * @param tags {@link #tags}.
   */
  private DynamicTagBuilder(Registry<T> registry, List<ResourceLocation> tags) {
    this.tags = tags;
    this.registry = registry;
  }

  /**
   * Provides a {@link DynamicTagBuilder} for the specified {@link Registry}.
   *
   * @param registry {@link #registry}.
   * @param tags {@link #tags}.
   * @param <R> game object type to which the tags will be applied to.
   * @return {@link DynamicTagBuilder}.
   */
  @SafeVarargs
  public static <R> DynamicTagBuilder<R> of(Registry<R> registry, TagKey<? extends R>... tags) {
    return new DynamicTagBuilder<>(registry, Arrays.stream(tags).map(TagKey::location).toList());
  }

  /**
   * Provides a {@link DynamicTagBuilder} for the specified {@link Registry}.
   *
   * @param registryKey {@link #registry}.
   * @param tags {@link #tags}.
   * @param <R> game object type to which the tags will be applied to.
   * @return {@link DynamicTagBuilder}.
   */
  @SafeVarargs
  public static <R> DynamicTagBuilder<R> of(ResourceKey<? extends Registry<R>> registryKey, TagKey<? extends R>... tags) {
    return of((Registry<R>) BuiltInRegistries.REGISTRY.get(registryKey.location()), tags);
  }

  /**
   * Returns the list of tags to build.
   *
   * @return {@link #tags}.
   */
  public List<ResourceLocation> getTags() {
    return tags;
  }

  /**
   * Returns the list of paths for each tag.
   *
   * @return list of paths for each tag.
   */
  public List<ResourceLocation> getPaths() {
    return getTags().stream().map(tag -> DynamicResourceType.TAG.getPath(ResourceLocation.fromNamespaceAndPath(tag.getNamespace(), this.registry.key().location().getPath() + "s/" + tag.getPath()))).toList();
  }

  /**
   * Add a new unique {@link TagEntry}.
   *
   * @param entry new unique {@link TagEntry}.
   * @return this builder.
   */
  @NotNull
  @Override
  public DynamicTagBuilder<T> add(@NotNull TagEntry entry) {
    if (keys.add(entry.toString())) {
      return (DynamicTagBuilder<T>) super.add(entry);
    }
    return this;
  }

  /**
   * Add a new tag to build.
   *
   * @param tagKey new tag.
   * @return this builder.
   */
  public DynamicTagBuilder<T> addTag(TagKey<? extends T> tagKey) {
    return (DynamicTagBuilder<T>) addTag(tagKey.location());
  }

  /**
   * Add a new element to which apply the tags.
   *
   * @param element new element.
   * @return this builder.
   */
  public DynamicTagBuilder<T> addElement(T element) {
    return (DynamicTagBuilder<T>) addElement(Objects.requireNonNull(registry.getKey(element)));
  }

  /**
   * Add a list of elements to which apply the tags.
   *
   * @param elements list of elements.
   * @return this builder.
   */
  public DynamicTagBuilder<T> addElements(Collection<? extends T> elements) {
    for (T element : elements) {
      addElement(element);
    }
    return this;
  }

  /**
   * Add a list of elements to which apply the tags.
   *
   * @param elements list of elements.
   * @return this builder.
   */
  public DynamicTagBuilder<T> addElements(T... elements) {
    for (T element : elements) {
      addElement(element);
    }
    return this;
  }

  /**
   * Returns a new {@link JsonElement} for this builder.
   *
   * @return {@link JsonElement}.
   */
  public JsonElement json() {
    return TagFile.CODEC.encodeStart(JsonOps.INSTANCE, new TagFile(build(), false)).getOrThrow(false, error -> {});
  }
}
