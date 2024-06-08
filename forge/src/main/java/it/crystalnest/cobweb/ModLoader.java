package it.crystalnest.cobweb;

import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.ApiStatus;

/**
 * Mod loader.
 */
@ApiStatus.Internal
@Mod(Constants.MOD_ID)
public final class ModLoader {
  /**
   * Mod initialization.
   */
  public ModLoader() {
    CommonModLoader.init();
  }
}
