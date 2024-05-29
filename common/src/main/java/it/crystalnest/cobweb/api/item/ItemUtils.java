package it.crystalnest.cobweb.api.item;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * Utility methods for {@link Item Items}.
 */
public final class ItemUtils {
  private ItemUtils() {}

  /**
   * Returns the in-game {@link ResourceLocation} of the item passed as parameter.
   *
   * @param item item.
   * @return {@link ResourceLocation} of the given item.
   */
  public static ResourceLocation getKey(Item item) {
    return Registry.ITEM.getKey(item);
  }

  /**
   * Returns the in-game ID of the item passed as parameter.
   *
   * @param item item.
   * @return in-game ID of the given item.
   */
  public static String getStringKey(Item item) {
    return ItemUtils.getKey(item).toString();
  }
}
