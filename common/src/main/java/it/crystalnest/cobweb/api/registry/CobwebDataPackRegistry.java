package it.crystalnest.cobweb.api.registry;

import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

import java.util.Objects;

/**
 * Describes a registry whose entries are loaded from data packs by Minecraft.
 * Resolve loaded entries through the registry access of the current world or resource load.
 *
 * @param <T> entry type.
 */
public final class CobwebDataPackRegistry<T> {
  /**
   * Native registry identity.
   */
  private final ResourceKey<Registry<T>> key;

  /**
   * Codec for complete data pack entries.
   */
  private final Codec<T> codec;

  /**
   * Native loading lifetime.
   */
  private final Lifetime lifetime;

  CobwebDataPackRegistry(ResourceKey<Registry<T>> key, Codec<T> codec, Lifetime lifetime) {
    this.key = Objects.requireNonNull(key);
    this.codec = Objects.requireNonNull(codec);
    this.lifetime = lifetime;
  }

  /**
   * Returns the native registry key.
   *
   * @return native registry key.
   */
  public ResourceKey<Registry<T>> key() {
    return key;
  }

  /**
   * Returns the codec for complete entries stored in data packs.
   *
   * @return data pack entry codec.
   */
  public Codec<T> codec() {
    return codec;
  }

  /**
   * Returns when Minecraft loads the entries of this registry.
   *
   * @return native loading lifetime.
   */
  public Lifetime lifetime() {
    return lifetime;
  }

  /**
   * Native loading lifetimes of data pack registries.
   */
  public enum Lifetime {
    /**
     * Loaded when opening a world and retained across resource reloads.
     */
    WORLD,

    /**
     * Loaded when opening a world and rebuilt on data pack reload.
     */
    RELOADABLE
  }
}
