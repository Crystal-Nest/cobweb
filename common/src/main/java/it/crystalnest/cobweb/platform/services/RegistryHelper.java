package it.crystalnest.cobweb.platform.services;

import it.crystalnest.cobweb.api.registry.CobwebRegister;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Platform specific registration helper.
 *
 * @param <T> actual register type.
 */
public abstract class RegistryHelper<T extends CobwebRegister<?>> {
  /**
   * Map linking all mods that use the API to all the registries they use.<br />
   * Used to avoid creating and registering multiple instances of the same register.
   */
  protected final Map<String, Map<ResourceLocation, T>> registries = new HashMap<>();

  /**
   * Provides a {@link CobwebRegister} for the specified mod and {@link Registry}.
   *
   * @param registry Minecraft {@link Registry}.
   * @param namespace mod ID.
   * @param <R> register type.
   * @return {@link CobwebRegister}.
   */
  public <R> CobwebRegister<R> of(Registry<R> registry, String namespace) {
    return of(registry.key(), namespace);
  }

  /**
   * Provides a {@link CobwebRegister} for the specified mod and {@link Registry}.
   *
   * @param registryKey Minecraft {@link Registry} key.
   * @param namespace mod ID.
   * @param <R> register type.
   * @return {@link CobwebRegister}.
   */
  public abstract <R> CobwebRegister<R> of(ResourceKey<? extends Registry<R>> registryKey, String namespace);
}
