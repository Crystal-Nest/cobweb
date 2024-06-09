package it.crystalnest.cobweb.api.registry;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * Concise way to update Minecraft {@link Registry}s.
 */
public final class RegisterProvider {
  /**
   * Mod ID used to identify the mod registering objects via the Minecraft {@link Registry}s.
   */
  private final String namespace;

  /**
   * @param namespace {@link #namespace}.
   */
  public RegisterProvider(String namespace) {
    this.namespace = namespace;
  }

  /**
   * Provides {@link Register} for the specified Minecraft {@link Registry}.
   *
   * @param <R> type hold by Minecraft {@link Registry}.
   * @param registryKey Minecraft {@link ResourceKey} for a register.
   * @return {@link Register}.
   */
  @SuppressWarnings("unchecked")
  public <R> Register<R> of(ResourceKey<? extends Registry<R>> registryKey) {
    return of((Registry<R>) Registry.REGISTRY.get(registryKey.location()));
  }

  /**
   * Provides {@link Register} for the specified Minecraft {@link Registry}.
   *
   * @param <R> type hold by Minecraft {@link Registry}.
   * @param registry the actual Minecraft {@link Registry}.
   * @return {@link Register}.
   */
  public <R> Register<R> of(Registry<R> registry) {
    return (key, value) -> Registry.register(registry, new ResourceLocation(namespace, key), value);
  }
}
