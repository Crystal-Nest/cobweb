package it.crystalnest.cobweb.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Injects into {@link DedicatedServerProperties} to reuse its resource pack prompt parsing.
 */
@Mixin(DedicatedServerProperties.class)
public interface DedicatedServerPropertiesAccessor {
  /**
   * Parses a resource pack prompt using Minecraft's server properties rules.
   *
   * @param prompt serialized text component.
   * @return decoded prompt, or null when absent or invalid.
   */
  @Nullable
  @Invoker("parseResourcePackPrompt")
  static Component invokeParseResourcePackPrompt(String prompt) {
    throw new AssertionError();
  }
}
