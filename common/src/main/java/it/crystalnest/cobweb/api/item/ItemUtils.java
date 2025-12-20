package it.crystalnest.cobweb.api.item;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/**
 * Utility methods for {@link Item Items}.
 */
public final class ItemUtils {
  private ItemUtils() {}

  /**
   * Returns the in-game {@link Identifier} of the item passed as parameter.
   *
   * @param item item.
   * @return {@link Identifier} of the given item.
   */
  public static Identifier getKey(Item item) {
    return BuiltInRegistries.ITEM.getKey(item);
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
