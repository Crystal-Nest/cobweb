package it.crystalnest.cobweb.api.registry;

import it.crystalnest.cobweb.platform.Services;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.alchemy.Potion;

/**
 * Cobweb unified registering API.
 */
public final class CobwebRegistry {
  private CobwebRegistry() {}

  /**
   * Provides a {@link CobwebRegister} for the specified mod and {@link Registry}.
   *
   * @param registry Minecraft {@link Registry}.
   * @param namespace mod ID.
   * @param <R> register type.
   * @return {@link CobwebRegister}.
   */
  public static <R> CobwebRegister<R> of(Registry<R> registry, String namespace) {
    return Services.REGISTRY.of(registry, namespace);
  }

  /**
   * Provides a {@link CobwebRegister} for the specified mod and {@link Registry}.
   *
   * @param registryKey Minecraft {@link Registry} key.
   * @param namespace mod ID.
   * @param <R> register type.
   * @return {@link CobwebRegister}.
   */
  public static <R> CobwebRegister<R> of(ResourceKey<? extends Registry<R>> registryKey, String namespace) {
    return Services.REGISTRY.of(registryKey, namespace);
  }

  /**
   * Provides a {@link CobwebRegister.Items} for the specified mod.
   *
   * @param namespace mod ID.
   * @return {@link CobwebRegister.Items}.
   */
  public static CobwebRegister.Items ofItems(String namespace) {
    return Services.REGISTRY.ofItems(namespace);
  }

  /**
   * Provides a {@link CobwebRegister.Blocks} for the specified mod.
   *
   * @param namespace mod ID.
   * @return {@link CobwebRegister.Blocks}.
   */
  public static CobwebRegister.Blocks ofBlocks(String namespace) {
    return Services.REGISTRY.ofBlocks(namespace);
  }

  /**
   * Provides a {@link CobwebRegister} of {@link EntityType}s for the specified mod.
   *
   * @param namespace mod ID.
   * @return {@link CobwebRegister} of {@link EntityType}s.
   */
  public static CobwebRegister<EntityType<?>> ofEntityTypes(String namespace) {
    return of(Registries.ENTITY_TYPE, namespace);
  }

  /**
   * Provides a {@link CobwebRegister} of {@link MobEffect}s for the specified mod.
   *
   * @param namespace mod ID.
   * @return {@link CobwebRegister} of {@link MobEffect}s.
   */
  public static CobwebRegister<MobEffect> ofMobEffects(String namespace) {
    return of(Registries.MOB_EFFECT, namespace);
  }

  /**
   * Provides a {@link CobwebRegister} of {@link Potion}s for the specified mod.
   *
   * @param namespace mod ID.
   * @return {@link CobwebRegister} of {@link Potion}s.
   */
  public static CobwebRegister<Potion> ofPotions(String namespace) {
    return of(Registries.POTION, namespace);
  }

  /**
   * Provides a {@link CobwebRegister} of {@link CreativeModeTab}s for the specified mod.
   *
   * @param namespace mod ID.
   * @return {@link CobwebRegister} of {@link CreativeModeTab}s.
   */
  public static CobwebRegister<CreativeModeTab> ofCreativeModeTabs(String namespace) {
    return of(Registries.CREATIVE_MODE_TAB, namespace);
  }
}
