package it.crystalnest.cobweb;

import it.crystalnest.cobweb.api.block.BlockUtils;
import net.minecraft.world.level.block.Blocks;

/**
 * Common mod loader.
 */
public final class CommonModLoader {
  private CommonModLoader() {}

  /**
   * Initialize common operations across loaders.
   */
  public static void init() {
    Constants.LOGGER.info(BlockUtils.getStringKey(Blocks.AMETHYST_BLOCK));
  }
}
