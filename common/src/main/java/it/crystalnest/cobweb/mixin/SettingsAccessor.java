package it.crystalnest.cobweb.mixin;

import net.minecraft.server.dedicated.Settings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Properties;

/**
 * Injects into {@link Settings} to read effective dedicated server properties.
 */
@Mixin(Settings.class)
public interface SettingsAccessor {
  /**
   * Returns the properties Minecraft loaded and normalized.
   *
   * @return native properties for read-only access.
   */
  @Accessor("properties")
  Properties getProperties();
}
