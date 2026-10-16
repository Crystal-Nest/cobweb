package it.crystalnest.cobweb.api.registry;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.Collection;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Loader agnostic register over native implementations. Factories run immediately on Fabric
 * and during native registration on NeoForge. Binding, equality and lookup follow the loader.
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
   * Identifies the native registry without resolving registered values.
   * @return native registry key, available before entries are bound.
   */
  ResourceKey<? extends Registry<R>> registryKey();

  /**
   * Accesses the full native static registry, including entries from other namespaces.
   * Fabric resolves it when this register is obtained. On NeoForge lookup throws until
   * the registry is installed, unless this register created the registry itself.
   * Entry registration/binding may still be incomplete even when the registry is available.
   * World-loaded and reloadable registries must instead be obtained from the world's registry access.
   * @return native registry.
   * @throws IllegalStateException on NeoForge when the registry is not yet installed and was not created by this register.
   */
  Registry<R> registry();

  /**
   * Declares a native alias from an old identifier to its replacement.
   * Fabric applies it immediately, validating writes, conflicts and cycles; declare the target first
   * to avoid a missing-target warning. NeoForge queues it until registration, applying aliases
   * before entries. Repeated declarations and invalid aliases follow native loader rules.
   * An alias does not create another entry or change the target's canonical identifier.
   * @param from old full identifier.
   * @param to replacement full identifier.
   */
  void addAlias(Identifier from, Identifier to);

  /**
   * Declares an alias with both paths in this register's namespace.
   * @param from old path.
   * @param to replacement path.
   */
  default void addAlias(String from, String to) {
    addAlias(Identifier.fromNamespaceAndPath(namespace(), from), Identifier.fromNamespaceAndPath(namespace(), to));
  }

  /**
   * Unmodifiable live view of this register's declared entries in declaration order.
   * NeoForge entries may not yet be bound. Other mods' entries are not included.
   *
   * @return declared entry handles.
   */
  Collection<CobwebEntry<? extends R>> entries();

  /**
   * Creates a tag key in this namespace; does not generate tag contents.
   * @param path tag path.
   * @return tag key.
   */
  default TagKey<R> createTagKey(String path) {
    return createTagKey(Identifier.fromNamespaceAndPath(namespace(), path));
  }

  /**
   * Creates a tag key with an explicit namespace; does not generate tag contents.
   * @param id full tag identifier.
   * @return tag key.
   */
  default TagKey<R> createTagKey(Identifier id) {
    return TagKey.create(registryKey(), id);
  }

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
   * Registers a value using its full identifier at native registration time.
   * @param name value path within this register's namespace.
   * @param factory identifier-aware value factory.
   * @param <T> actual value type.
   * @return native-backed entry handle.
   */
  default <T extends R> CobwebEntry<T> register(String name, Function<Identifier, ? extends T> factory) {
    Objects.requireNonNull(factory);
    Identifier id = Identifier.fromNamespaceAndPath(namespace(), name);
    return register(name, () -> factory.apply(id));
  }

  /**
   * Loader agnostic register for {@link Item}s and {@link BlockItem}s.
   */
  interface Items extends CobwebRegister<Item> {
    /**
     * Registers the {@link Item} returned by the given supplier.<br>
     * Automatically sets the Item ID in its properties.
     *
     * @param name item name.
     * @param supplier item supplier.
     * @return registered item holder.
     * @param <T> item type.
     */
    default <T extends Item> CobwebEntry<T> registerItem(String name, Function<Item.Properties, ? extends T> supplier) {
      return registerItem(name, supplier, Item.Properties::new);
    }

    /**
     * Registers an item with supplied properties. The registration ID is assigned after customization.
     * The properties factory runs at native registration time and must supply independent mutable properties.
     * @param name item path.
     * @param constructor item constructor.
     * @param properties properties factory.
     * @param <T> item type.
     * @return item entry.
     */
    default <T extends Item> CobwebEntry<T> registerItem(String name, Function<Item.Properties, ? extends T> constructor, Supplier<Item.Properties> properties) {
      return register(name, id -> constructor.apply(properties.get().setId(ResourceKey.create(Registries.ITEM, id))));
    }

    /**
     * Registers an item by customizing fresh default properties at native registration time.
     * The transformation may return replacement properties; the registration ID is assigned afterwards.
     * @param name item path.
     * @param constructor item constructor.
     * @param properties properties transformation.
     * @param <T> item type.
     * @return item entry.
     */
    default <T extends Item> CobwebEntry<T> registerItem(String name, Function<Item.Properties, ? extends T> constructor, UnaryOperator<Item.Properties> properties) {
      return registerItem(name, constructor, () -> properties.apply(new Item.Properties()));
    }

    /**
     * Registers a plain item with vanilla default properties. No creative-tab entry or resources are generated.
     * @param name item path.
     * @return item entry.
     */
    default CobwebEntry<Item> registerSimpleItem(String name) {
      return registerItem(name, Item::new);
    }

    /**
     * Registers a plain item with lazily supplied properties.
     * @param name item path.
     * @param properties independent mutable properties factory.
     * @return plain item entry.
     */
    default CobwebEntry<Item> registerSimpleItem(String name, Supplier<Item.Properties> properties) {
      return registerItem(name, Item::new, properties);
    }

    /**
     * Registers a plain item by customizing fresh properties.
     * @param name item path.
     * @param properties transformation of fresh default properties.
     * @return plain item entry.
     */
    default CobwebEntry<Item> registerSimpleItem(String name, UnaryOperator<Item.Properties> properties) {
      return registerItem(name, Item::new, properties);
    }

    /**
     * Registers the custom {@link BlockItem} for the {@link Block} returned by the given supplier.<br>
     * Automatically sets the Item ID in its properties and makes it use the block description.
     *
     * @param name item name.
     * @param block block supplier.
     * @param properties item properties.
     * @param constructor custom block item constructor.
     * @return registered item holder.
     * @param <T> block item type.
     * @param <B> block type.
     */
    default <T extends BlockItem, B extends Block> CobwebEntry<T> registerBlockItemLike(String name, Supplier<B> block, Item.Properties properties, BiFunction<B, Item.Properties, T> constructor) {
      return registerBlockItemLike(name, block, constructor, () -> properties);
    }

    /**
     * Registers a custom block item with lazy properties and the block description prefix.
     * Both suppliers are evaluated at native registration time.
     * @param name item path.
     * @param block block supplier.
     * @param constructor block item constructor.
     * @param properties independent mutable properties factory.
     * @param <T> block item type.
     * @param <B> block type.
     * @return block item entry.
     */
    default <T extends BlockItem, B extends Block> CobwebEntry<T> registerBlockItemLike(String name, Supplier<? extends B> block, BiFunction<B, Item.Properties, ? extends T> constructor, Supplier<Item.Properties> properties) {
      return registerItem(name, props -> constructor.apply(block.get(), props), () -> properties.get().useBlockDescriptionPrefix());
    }

    /**
     * Registers a custom block item by transforming fresh properties at native registration time.
     * @param name item path.
     * @param block block supplier.
     * @param constructor block item constructor.
     * @param properties properties transformation (may return replacement properties).
     * @param <T> block item type.
     * @param <B> block type.
     * @return block item entry.
     */
    default <T extends BlockItem, B extends Block> CobwebEntry<T> registerBlockItemLike(String name, Supplier<? extends B> block, BiFunction<B, Item.Properties, ? extends T> constructor, UnaryOperator<Item.Properties> properties) {
      return registerBlockItemLike(name, block, constructor, () -> properties.apply(new Item.Properties()));
    }

    /**
     * Registers the {@link BlockItem} for the {@link Block} returned by the given supplier.<br>
     * Automatically sets the Item ID in its properties and makes it use the block description.
     *
     * @param name item name.
     * @param block block supplier.
     * @param properties item properties.
     * @return registered item holder.
     */
    default CobwebEntry<BlockItem> registerBlockItem(String name, Supplier<? extends Block> block, Item.Properties properties) {
      return registerBlockItemLike(name, block, properties, BlockItem::new);
    }

    /**
     * Registers the {@link BlockItem} for the {@link Block} returned by the given supplier.<br>
     * Automatically sets the Item ID in its properties and makes it use the block description.
     *
     * @param name item name.
     * @param block block supplier.
     * @return registered item holder.
     */
    default CobwebEntry<BlockItem> registerBlockItem(String name, Supplier<? extends Block> block) {
      return registerBlockItem(name, block, Item.Properties::new);
    }

    /**
     * Registers a block item with properties created at native registration time.
     * @param name item path.
     * @param block block supplier, resolved at native registration time.
     * @param properties independent mutable properties factory, evaluated at native registration time.
     * @return block item entry using the block description prefix.
     */
    default CobwebEntry<BlockItem> registerBlockItem(String name, Supplier<? extends Block> block, Supplier<Item.Properties> properties) {
      return registerBlockItemLike(name, block, BlockItem::new, properties);
    }

    /**
     * Registers a block item by customizing fresh properties at native registration time.
     * @param name item path.
     * @param block block supplier, resolved at native registration time.
     * @param properties transformation of fresh properties at native registration time.
     * @return block item entry using the block description prefix.
     */
    default CobwebEntry<BlockItem> registerBlockItem(String name, Supplier<? extends Block> block, UnaryOperator<Item.Properties> properties) {
      return registerBlockItemLike(name, block, BlockItem::new, properties);
    }

    /**
     * Uses the keyed holder's path and this item register's namespace, even for a block in another namespace.
     * The block value is resolved at native registration time; the holder need not be bound yet.
     * @param block keyed block holder.
     * @return block item with vanilla default properties and the block description prefix.
     */
    default CobwebEntry<BlockItem> registerBlockItem(Holder<? extends Block> block) {
      return registerBlockItem(block, Item.Properties::new);
    }

    /**
     * Uses the holder's path and this register's namespace, with lazy properties.
     * @param block keyed block holder, resolved at native registration time.
     * @param properties independent mutable properties factory, evaluated at native registration time.
     * @return block item entry.
     */
    default CobwebEntry<BlockItem> registerBlockItem(Holder<? extends Block> block, Supplier<Item.Properties> properties) {
      return registerBlockItem(block.unwrapKey().orElseThrow(() -> new IllegalArgumentException("Block holder must have a registry key")).identifier().getPath(), block::value, properties);
    }

    /**
     * Uses the holder's path and this register's namespace, transforming fresh properties.
     * @param block keyed block holder, resolved at native registration time.
     * @param properties properties transformation, evaluated at native registration time.
     * @return block item entry.
     */
    default CobwebEntry<BlockItem> registerBlockItem(Holder<? extends Block> block, UnaryOperator<Item.Properties> properties) {
      return registerBlockItem(block, () -> properties.apply(new Item.Properties()));
    }
  }

  /**
   * Loader agnostic register for {@link Block}s.
   */
  interface Blocks extends CobwebRegister<Block> {
    /**
     * Registers the {@link Block} returned by the given supplier.<br>
     * Automatically sets the Block ID in its properties.
     *
     * @param name block name.
     * @param supplier block supplier.
     * @return registered block holder.
     * @param <T> block type.
     */
    default <T extends Block> CobwebEntry<T> registerBlock(String name, Function<BlockBehaviour.Properties, ? extends T> supplier) {
      return registerBlock(name, supplier, BlockBehaviour.Properties::of);
    }

    /**
     * Registers a block with supplied properties, assigning its ID after customization.
     * @param name block path.
     * @param constructor block constructor.
     * @param properties independent mutable properties factory, evaluated at native registration time.
     * @param <T> block type.
     * @return block entry.
     */
    default <T extends Block> CobwebEntry<T> registerBlock(String name, Function<BlockBehaviour.Properties, ? extends T> constructor, Supplier<BlockBehaviour.Properties> properties) {
      return register(name, id -> constructor.apply(properties.get().setId(ResourceKey.create(Registries.BLOCK, id))));
    }

    /**
     * Registers a block by customizing fresh properties at native registration time.
     * @param name block path.
     * @param constructor block constructor.
     * @param properties transformation (may return replacement properties); the ID is assigned afterwards.
     * @param <T> block type.
     * @return block entry.
     */
    default <T extends Block> CobwebEntry<T> registerBlock(String name, Function<BlockBehaviour.Properties, ? extends T> constructor, UnaryOperator<BlockBehaviour.Properties> properties) {
      return registerBlock(name, constructor, () -> properties.apply(BlockBehaviour.Properties.of()));
    }

    /**
     * Registers a plain block with vanilla default properties.
     * @param name block path.
     * @return plain block with vanilla default properties; no block item or resources are generated.
     */
    default CobwebEntry<Block> registerSimpleBlock(String name) {
      return registerBlock(name, Block::new);
    }

    /**
     * Registers a plain block with lazily supplied properties.
     * @param name block path.
     * @param properties independent mutable properties factory.
     * @return plain block entry.
     */
    default CobwebEntry<Block> registerSimpleBlock(String name, Supplier<BlockBehaviour.Properties> properties) {
      return registerBlock(name, Block::new, properties);
    }

    /**
     * Registers a plain block by customizing fresh properties.
     * @param name block path.
     * @param properties transformation of fresh default properties.
     * @return plain block entry.
     */
    default CobwebEntry<Block> registerSimpleBlock(String name, UnaryOperator<BlockBehaviour.Properties> properties) {
      return registerBlock(name, Block::new, properties);
    }
  }

  /** Register for entity types using vanilla builders at native registration time. */
  interface Entities extends CobwebRegister<EntityType<?>> {
    /**
     * Registers an entity type using a default vanilla builder.
     * @param name entity path.
     * @param factory entity factory.
     * @param category entity category.
     * @param <E> entity type.
     * @return entity type entry built with vanilla defaults.
     */
    default <E extends Entity> CobwebEntry<EntityType<E>> registerEntityType(String name, EntityType.EntityFactory<E> factory, MobCategory category) {
      return registerEntityType(name, factory, category, UnaryOperator.identity());
    }

    /**
     * Registers an entity type, assigning its key after customizing a fresh vanilla builder.
     * Attributes, rendering and spawn rules must be registered separately.
     * @param name entity path.
     * @param factory entity factory.
     * @param category entity category.
     * @param builder builder transformation, evaluated at native registration time.
     * @param <E> entity type.
     * @return entity type entry.
     */
    default <E extends Entity> CobwebEntry<EntityType<E>> registerEntityType(String name, EntityType.EntityFactory<E> factory, MobCategory category, UnaryOperator<EntityType.Builder<E>> builder) {
      return register(name, id -> builder.apply(EntityType.Builder.of(factory, category)).build(ResourceKey.create(Registries.ENTITY_TYPE, id)));
    }
  }

  /** Register for data component types using native builder validation. */
  interface DataComponents extends CobwebRegister<DataComponentType<?>> {
    /**
     * Registers a component type from a fresh builder at native registration time.
     * Supply a stream codec or a persistent codec from which vanilla can derive one.
     * @param name component path.
     * @param builder builder transformation.
     * @param <D> component value type.
     * @return component type entry.
     */
    default <D> CobwebEntry<DataComponentType<D>> registerComponentType(String name, UnaryOperator<DataComponentType.Builder<D>> builder) {
      return register(name, () -> builder.apply(DataComponentType.builder()).build());
    }
  }
}
