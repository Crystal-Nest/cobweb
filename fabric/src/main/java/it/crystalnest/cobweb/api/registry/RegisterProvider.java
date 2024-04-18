package it.crystalnest.cobweb.api.registry;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;

/**
 * Concise way to update Minecraft {@link Registry}s.
 */
public final class RegisterProvider {
  /**
   * Mod ID used to identify the mod registering objects via the Minecraft {@link Registry}s.
   */
  private final String MOD_ID;

  /**
   * @param modId {@link #MOD_ID}.
   */
  public RegisterProvider(String modId) {
    this.MOD_ID = modId;
  }

  /**
   * Provides {@link Register} for the specified Minecraft {@link Registry}.
   *
   * @param <R> type hold by Minecraft {@link Registry}.
   * @param registry the actual Minecraft {@link Registry}.
   * @return {@link Register}.
   */
  public <R> Register<R> of(Registry<R> registry) {
    return (key, value) -> Registry.register(registry, new ResourceLocation(MOD_ID, key), value);
  }
}
