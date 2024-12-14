package it.crystalnest.cobweb.platform.services;

import it.crystalnest.cobweb.api.pack.dynamic.DynamicResourcePack;
import it.crystalnest.cobweb.api.pack.fixed.StaticResourcePack;
import it.crystalnest.cobweb.api.registry.CobwebRegister;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Platform specific registration helper.
 *
 * @param <T> actual register type.
 */
public abstract class RegistryHelper<T extends CobwebRegister<?>> {
  /**
   * Map linking all mods that use the API to all the registries they use.<br>
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

  /**
   * Provides a {@link CobwebRegister.Items} for the specified mod.
   *
   * @param namespace mod ID.
   * @return {@link CobwebRegister.Items}.
   */
  public abstract CobwebRegister.Items ofItems(String namespace);

  /**
   * Provides a {@link CobwebRegister.Blocks} for the specified mod.
   *
   * @param namespace mod ID.
   * @return {@link CobwebRegister.Blocks}.
   */
  public abstract CobwebRegister.Blocks ofBlocks(String namespace);

  /**
   * Registers a {@link DynamicResourcePack}.
   *
   * @param type {@link PackType}.
   * @param supplier {@link Supplier} for the {@link Pack} to register.
   */
  public abstract void registerDynamicResourcePack(PackType type, Supplier<Pack> supplier);

  /**
   * Registers a {@link StaticResourcePack}.
   *
   * @param pack {@link StaticResourcePack}.
   */
  public abstract void registerStaticResourcePack(StaticResourcePack pack);
}
