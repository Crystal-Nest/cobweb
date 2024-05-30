package it.crystalnest.cobweb.api.block;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

/**
 * Utility methods for {@link Block Blocks}.
 */
public final class BlockUtils {
  private BlockUtils() {}

  /**
   * Returns the in-game {@link ResourceLocation} of the block passed as parameter.
   *
   * @param block block.
   * @return {@link ResourceLocation} of the given block.
   */
  public static ResourceLocation getKey(Block block) {
    return Registry.BLOCK.getKey(block);
  }

  /**
   * Returns the in-game ID of the block passed as parameter.
   *
   * @param block block.
   * @return in-game ID of the given block.
   */
  public static String getStringKey(Block block) {
    return BlockUtils.getKey(block).toString();
  }
}
