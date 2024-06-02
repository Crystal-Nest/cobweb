package it.crystalnest.cobweb;

import net.fabricmc.api.ModInitializer;
import org.jetbrains.annotations.ApiStatus;

/**
 * Mod loader.
 */
@ApiStatus.Internal
public class ModLoader implements ModInitializer {
  @Override
  public void onInitialize() {
    CommonModLoader.init();
  }
}
