package it.crystalnest.cobweb.platform;

import com.mojang.serialization.Codec;
import it.crystalnest.cobweb.api.registry.CobwebDataPackRegistry;
import it.crystalnest.cobweb.api.registry.CobwebEntry;
import it.crystalnest.cobweb.api.registry.CobwebRegister;
import it.crystalnest.cobweb.api.registry.CobwebRegistry;
import it.crystalnest.cobweb.platform.services.RegistryHelper;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.fabricmc.fabric.api.event.registry.RegistryAttribute;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Fabric registration helper.
 */
public final class FabricRegistryHelper extends RegistryHelper<FabricRegistryHelper.DeferredRegister<?>> {
  @Override
  protected <T> void addDataPackRegistry(CobwebDataPackRegistry<T> definition, @Nullable Codec<T> networkCodec) {
    if (BuiltInRegistries.REGISTRY.containsKey(definition.key().identifier())) {
      throw new IllegalStateException("Registry already exists: " + definition.key().identifier());
    }
    if (definition.lifetime() == CobwebDataPackRegistry.Lifetime.RELOADABLE) {
      DynamicRegistries.registerReloadable(definition.key(), definition.codec());
    } else if (networkCodec != null) {
      DynamicRegistries.registerSynced(definition.key(), definition.codec(), networkCodec);
    } else {
      DynamicRegistries.register(definition.key(), definition.codec());
    }
  }

  @Override
  public List<RegistryDataLoader.RegistryData<?>> worldRegistries() {
    return DynamicRegistries.getWorldRegistries();
  }

  @Override
  public List<RegistryDataLoader.RegistryData<?>> reloadableRegistries() {
    return DynamicRegistries.getReloadableRegistries();
  }

  @Override
  protected <R> CobwebRegister<R> createRegistry(ResourceKey<Registry<R>> registryKey, String namespace, CobwebRegistry.Settings settings) {
    // Validate the entry namespace before mutating the root registry.
    Identifier.fromNamespaceAndPath(namespace, "validation");
    if (BuiltInRegistries.REGISTRY.containsKey(registryKey.identifier())) {
      throw new IllegalStateException("Registry already exists: " + registryKey.identifier());
    }
    FabricRegistryBuilder<R, ?> builder = settings.defaultKey() == null
      ? FabricRegistryBuilder.create(registryKey)
      : FabricRegistryBuilder.createDefaulted(registryKey, settings.defaultKey());
    if (settings.synced()) {
      builder.attribute(RegistryAttribute.SYNCED);
    }
    builder.buildAndRegister();
    return of(registryKey, namespace);
  }

  @Override
  public CreativeModeTab.Builder createCreativeModeTabBuilder() {
    return FabricCreativeModeTab.builder();
  }

  @Override
  @SuppressWarnings("unchecked")
  public <R> DeferredRegister<R> of(ResourceKey<? extends Registry<R>> registryKey, String namespace) {
    return (DeferredRegister<R>) registries.computeIfAbsent(namespace, key -> new ConcurrentHashMap<>()).computeIfAbsent(registryKey.identifier(), key -> {
      DeferredRegister<?> register;
      if (registryKey.equals(Registries.ITEM)) {
        register = new DeferredRegister.Items(namespace);
      } else if (registryKey.equals(Registries.BLOCK)) {
        register = new DeferredRegister.Blocks(namespace);
      } else if (registryKey.equals(Registries.ENTITY_TYPE)) {
        register = new DeferredRegister.Entities(namespace);
      } else if (registryKey.equals(Registries.POTION)) {
        register = new DeferredRegister.Potions(namespace);
      } else if (registryKey.equals(Registries.CREATIVE_MODE_TAB)) {
        register = new DeferredRegister.CreativeModeTabs(namespace);
      } else if (registryKey.equals(Registries.DATA_COMPONENT_TYPE) || registryKey.equals(Registries.ENCHANTMENT_EFFECT_COMPONENT_TYPE)) {
        register = new DeferredRegister.DataComponents(registryKey.equals(Registries.DATA_COMPONENT_TYPE) ? Registries.DATA_COMPONENT_TYPE : Registries.ENCHANTMENT_EFFECT_COMPONENT_TYPE, namespace);
      } else {
        register = new DeferredRegister<>(registryKey, namespace);
      }
      return register;
    });
  }

  /**
   * Deferred register.
   *
   * @param <R> registry type.
   */
  public static class DeferredRegister<R> implements CobwebRegister<R> {
    /**
     * Declared handles; native registration timing is unaffected.
     */
    private final List<CobwebEntry<? extends R>> entries = new ArrayList<>();

    /**
     * Unmodifiable live view, avoiding a new copy on each inspection.
     */
    private final Collection<CobwebEntry<? extends R>> entriesView = Collections.unmodifiableList(entries);

    /**
     * Native registry.
     */
    private final Registry<R> registry;

    /**
     * Namespace.
     */
    private final String namespace;

    /**
     * @param registryKey Minecraft {@link Registry} key.
     * @param namespace {@link #namespace}.
     */
    @SuppressWarnings("unchecked")
    private DeferredRegister(ResourceKey<? extends Registry<R>> registryKey, String namespace) {
      this.registry = (Registry<R>) BuiltInRegistries.REGISTRY.get(registryKey.identifier()).orElseThrow().value();
      this.namespace = namespace;
    }

    @Override
    public String namespace() {
      return namespace;
    }

    @Override
    public ResourceKey<? extends Registry<R>> registryKey() {
      return registry.key();
    }

    @Override
    public Registry<R> registry() {
      return registry;
    }

    @Override
    public void addAlias(Identifier from, Identifier to) {
      registry.addAlias(from, to);
    }

    @Override
    public Collection<CobwebEntry<? extends R>> entries() {
      return entriesView;
    }

    @Override
    public <T extends R> CobwebEntry<T> register(String name, Supplier<? extends T> supplier) {
      CobwebEntry<T> entry = new Entry<>(Registry.registerForHolder(registry, Identifier.fromNamespaceAndPath(namespace, name), supplier.get()));
      entries.add(entry);
      return entry;
    }

    /**
     * Deferred register for {@link Item}s.
     */
    public static final class Items extends DeferredRegister<Item> implements CobwebRegister.Items {
      /**
       * @param namespace {@link #namespace}.
       */
      private Items(String namespace) {
        super(Registries.ITEM, namespace);
      }
    }

    /**
     * Deferred register for {@link Block}s.
     */
    public static final class Blocks extends DeferredRegister<Block> implements CobwebRegister.Blocks {
      /**
       * @param namespace {@link #namespace}.
       */
      private Blocks(String namespace) {
        super(Registries.BLOCK, namespace);
      }
    }

    /**
     * Specialized register for entity types.
     */
    public static final class Entities extends DeferredRegister<EntityType<?>> implements CobwebRegister.Entities {
      /**
       * @param namespace namespace of entries declared through this register.
       */
      private Entities(String namespace) {
        super(Registries.ENTITY_TYPE, namespace);
      }
    }

    /**
     * Specialized register for potions.
     */
    public static final class Potions extends DeferredRegister<Potion> implements CobwebRegister.Potions {
      /**
       * @param namespace namespace of entries declared through this register.
       */
      private Potions(String namespace) {
        super(Registries.POTION, namespace);
      }
    }

    /**
     * Specialized register for creative mode tabs.
     */
    public static final class CreativeModeTabs extends DeferredRegister<CreativeModeTab> implements CobwebRegister.CreativeModeTabs {
      /**
       * @param namespace namespace of entries declared through this register.
       */
      private CreativeModeTabs(String namespace) {
        super(Registries.CREATIVE_MODE_TAB, namespace);
      }
    }

    /**
     * Specialized register for data component types.
     */
    public static final class DataComponents extends DeferredRegister<DataComponentType<?>> implements CobwebRegister.DataComponents {
      /**
       * @param registryKey native data component registry key.
       * @param namespace namespace of entries declared through this register.
       */
      private DataComponents(ResourceKey<Registry<DataComponentType<?>>> registryKey, String namespace) {
        super(registryKey, namespace);
      }
    }
  }

  /**
   * Handle retaining Fabric's native registered reference and its identity-based equality.
   *
   * @param <T> game object type.
   */
  private static final class Entry<T> extends CobwebEntry<T> {
    /**
     * @param holder native reference holder for the registered entry.
     */
    private Entry(Holder.Reference<T> holder) {
      super(holder);
    }
  }
}
