package it.crystalnest.cobweb.api.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Loader agnostic register.
 *
 * @param <R> register type.
 */
public interface CobwebRegister<R> {
  /**
   * Register namespace (mod ID).
   *
   * @return register namespace.
   */
  String namespace();

  /**
   * Registers the game object returned by the given supplier.
   *
   * @param name game object name.
   * @param supplier game object supplier.
   * @param <T> game object type.
   * @return registered game object holder.
   */
  <T extends R> CobwebEntry<T> register(String name, Supplier<? extends T> supplier);

  /**
   * Loader agnostic register for {@link Item}s and {@link BlockItem}s.
   */
  interface Items extends CobwebRegister<Item> {
    /**
     * Registers the {@link Item} returned by the given supplier.<br />
     * Automatically sets the Item ID in its properties.
     *
     * @param name item name.
     * @param supplier item supplier.
     * @return registered item holder.
     */
    default <T extends Item> CobwebEntry<T> registerItem(String name, Function<Item.Properties, T> supplier) {
      return register(name, () -> supplier.apply(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(namespace(), name)))));
    }
    /**
     * Registers the {@link BlockItem} for the {@link Block} returned by the given supplier.<br />
     * Automatically sets the Item ID in its properties and makes it use the block description.
     *
     * @param name item name.
     * @param block block supplier.
     * @param properties item properties.
     * @return registered item holder.
     */
    default CobwebEntry<BlockItem> registerBlockItem(String name, Supplier<? extends Block> block, Item.Properties properties) {
      return register(name, () -> new BlockItem(block.get(), properties.setId(ResourceKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(namespace(), name))).useBlockDescriptionPrefix()));
    }
    /**
     * Registers the {@link BlockItem} for the {@link Block} returned by the given supplier.<br />
     * Automatically sets the Item ID in its properties and makes it use the block description.
     *
     * @param name item name.
     * @param block block supplier.
     * @return registered item holder.
     */
    default CobwebEntry<BlockItem> registerBlockItem(String name, Supplier<? extends Block> block) {
      return registerBlockItem(name, block, new Item.Properties());
    }
  }

  /**
   * Loader agnostic register for {@link Block}s.
   */
  interface Blocks extends CobwebRegister<Block> {
    /**
     * Registers the {@link Block} returned by the given supplier.<br />
     * Automatically sets the Block ID in its properties.
     *
     * @param name block name.
     * @param supplier block supplier.
     * @return registered block holder.
     */
    default <T extends Block> CobwebEntry<T> registerBlock(String name, Function<Block.Properties, T> supplier) {
      return register(name, () -> supplier.apply(Block.Properties.of().setId(ResourceKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(namespace(), name)))));
    }
  }
}
