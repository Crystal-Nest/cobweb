package it.crystalnest.cobweb.platform;

import com.mojang.serialization.Codec;
import it.crystalnest.cobweb.api.registry.CobwebDataPackRegistry;
import it.crystalnest.cobweb.api.registry.CobwebEntry;
import it.crystalnest.cobweb.api.registry.CobwebRegister;
import it.crystalnest.cobweb.api.registry.CobwebRegistry;
import it.crystalnest.cobweb.platform.services.RegistryHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
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
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DataPackRegistriesHooks;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NewDatapackRegistryEvent;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * NeoForge registration helper.
 */
public final class NeoForgeRegistryHelper extends RegistryHelper<NeoForgeRegistryHelper.Register<?>> {
  /**
   * NeoForge event bus.
   */
  private IEventBus bus = null;

  /**
   * Declarations gathered from mod constructors before the shared native registry event.
   */
  private final List<Consumer<NewDatapackRegistryEvent>> dataPackRegistries = new ArrayList<>();

  @Override
  protected <T> void addDataPackRegistry(CobwebDataPackRegistry<T> definition, @Nullable Codec<T> networkCodec) {
    dataPackRegistries.add(event -> {
      var key = definition.key();
      if (
        BuiltInRegistries.REGISTRY.containsKey(key.identifier()) ||
        Stream.of(RegistryDataLoader.WORLD_REGISTRIES, RegistryDataLoader.DIMENSION_REGISTRIES, RegistryDataLoader.RELOADABLE_REGISTRIES).flatMap(Collection::stream).anyMatch(registry -> registry.key().equals(key))
      ) {
        throw new IllegalStateException("Registry already exists: " + key.identifier());
      }
      if (definition.lifetime() == CobwebDataPackRegistry.Lifetime.RELOADABLE) {
        event.reloadableRegistry(key, definition.codec());
      } else {
        event.worldRegistry(key, definition.codec(), networkCodec);
      }
    });
  }

  @Override
  public List<RegistryDataLoader.RegistryData<?>> worldRegistries() {
    return DataPackRegistriesHooks.getWorldRegistries();
  }

  @Override
  public List<RegistryDataLoader.RegistryData<?>> reloadableRegistries() {
    return DataPackRegistriesHooks.getReloadableRegistries();
  }

  @Override
  protected <R> CobwebRegister<R> createRegistry(ResourceKey<Registry<R>> registryKey, String namespace, CobwebRegistry.Settings settings) {
    Register<R> register = of(registryKey, namespace);
    register.register.makeRegistry(builder -> {
      builder.sync(settings.synced());
      if (settings.defaultKey() != null) {
        builder.defaultKey(settings.defaultKey());
      }
    });
    return register;
  }

  @Override
  public CreativeModeTab.Builder createCreativeModeTabBuilder() {
    return CreativeModeTab.builder();
  }

  @Override
  @SuppressWarnings("unchecked")
  public <R> Register<R> of(ResourceKey<? extends Registry<R>> registryKey, String namespace) {
    return (Register<R>) registries.computeIfAbsent(namespace, key -> new ConcurrentHashMap<>()).computeIfAbsent(registryKey.identifier(), key -> {
      Register<?> register;
      if (registryKey.equals(Registries.ITEM)) {
        register = new Register.Items(namespace);
      } else if (registryKey.equals(Registries.BLOCK)) {
        register = new Register.Blocks(namespace);
      } else if (registryKey.equals(Registries.ENTITY_TYPE)) {
        register = new Register.Entities(namespace);
      } else if (registryKey.equals(Registries.POTION)) {
        register = new Register.Potions(namespace);
      } else if (registryKey.equals(Registries.CREATIVE_MODE_TAB)) {
        register = new Register.CreativeModeTabs(namespace);
      } else if (registryKey.equals(Registries.DATA_COMPONENT_TYPE) || registryKey.equals(Registries.ENCHANTMENT_EFFECT_COMPONENT_TYPE)) {
        register = new Register.DataComponents(registryKey.equals(Registries.DATA_COMPONENT_TYPE) ? Registries.DATA_COMPONENT_TYPE : Registries.ENCHANTMENT_EFFECT_COMPONENT_TYPE, namespace);
      } else {
        register = new Register<>(registryKey, namespace);
      }
      register.register(bus);
      return register;
    });
  }

  /**
   * Saves the given event bus to register game objects.
   *
   * @param bus {@link IEventBus}.
   */
  public void register(IEventBus bus) {
    this.bus = bus;
    bus.addListener((NewDatapackRegistryEvent event) -> {
      closeDataPackRegistries();
      dataPackRegistries.forEach(registry -> registry.accept(event));
      dataPackRegistries.clear();
    });
  }

  /**
   * Extension for {@link DeferredRegister} to implement {@link CobwebRegister}.
   *
   * @param <R> registry type.
   */
  public static class Register<R> implements CobwebRegister<R> {
    /**
     * Declared handles; native registration timing is unaffected.
     */
    private final List<CobwebEntry<? extends R>> entries = new ArrayList<>();

    /**
     * Unmodifiable live view, avoiding a new copy on each inspection.
     */
    private final Collection<CobwebEntry<? extends R>> entriesView = Collections.unmodifiableList(entries);

    /**
     * Wrapped instance of {@link DeferredRegister}.
     */
    private final DeferredRegister<R> register;

    /**
     * Namespace.
     */
    private final String namespace;

    /**
     * Creates a wrapper for a native deferred register with the supplied key and namespace.
     *
     * @param registryKey {@link DeferredRegister#registryKey}.
     * @param namespace {@link DeferredRegister#namespace}.
     */
    private Register(ResourceKey<? extends Registry<R>> registryKey, String namespace) {
      register = DeferredRegister.create(registryKey, namespace);
      this.namespace = namespace;
    }

    /**
     * Wrapper around {@link DeferredRegister#register(IEventBus)}.
     *
     * @param bus {@link IEventBus}.
     */
    public void register(IEventBus bus) {
      register.register(bus);
    }

    @Override
    public String namespace() {
      return namespace;
    }

    @Override
    public ResourceKey<? extends Registry<R>> registryKey() {
      return register.getRegistryKey();
    }

    @Override
    public Registry<R> registry() {
      return register.getRegistry().get();
    }

    @Override
    public void addAlias(Identifier from, Identifier to) {
      register.addAlias(from, to);
    }

    @Override
    public Collection<CobwebEntry<? extends R>> entries() {
      return entriesView;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends R> CobwebEntry<T> register(String name, Supplier<? extends T> supplier) {
      CobwebEntry<T> entry = new Entry<>((Holder.Reference<T>) (Holder<?>) register.register(name, supplier));
      entries.add(entry);
      return entry;
    }

    /**
     * Deferred register for {@link Item}s.
     */
    public static final class Items extends Register<Item> implements CobwebRegister.Items {
      /**
       * Creates an item register for the supplied namespace.
       *
       * @param namespace {@link DeferredRegister#namespace}.
       */
      private Items(String namespace) {
        super(Registries.ITEM, namespace);
      }
    }

    /**
     * Deferred register for {@link Block}s.
     */
    public static final class Blocks extends Register<Block> implements CobwebRegister.Blocks {
      /**
       * Creates a block register for the supplied namespace.
       *
       * @param namespace {@link DeferredRegister#namespace}.
       */
      private Blocks(String namespace) {
        super(Registries.BLOCK, namespace);
      }
    }

    /**
     * Specialized register for entity types.
     */
    public static final class Entities extends Register<EntityType<?>> implements CobwebRegister.Entities {
      private Entities(String namespace) {
        super(Registries.ENTITY_TYPE, namespace);
      }
    }

    /**
     * Specialized register for potions.
     */
    public static final class Potions extends Register<Potion> implements CobwebRegister.Potions {
      private Potions(String namespace) {
        super(Registries.POTION, namespace);
      }
    }

    /**
     * Specialized register for creative mode tabs.
     */
    public static final class CreativeModeTabs extends Register<CreativeModeTab> implements CobwebRegister.CreativeModeTabs {
      private CreativeModeTabs(String namespace) {
        super(Registries.CREATIVE_MODE_TAB, namespace);
      }
    }

    /**
     * Specialized register for data component types.
     */
    public static final class DataComponents extends Register<DataComponentType<?>> implements CobwebRegister.DataComponents {
      private DataComponents(ResourceKey<Registry<DataComponentType<?>>> registryKey, String namespace) {
        super(registryKey, namespace);
      }
    }
  }

  /**
   * Delegates NeoForge's holder extensions and key-based equality to the native registration.
   *
   * @param <T> game object type.
   */
  private static final class Entry<T> extends CobwebEntry<T> {
    private Entry(Holder.Reference<T> holder) {
      super(holder);
    }

    @Override
    public @NonNull Holder<T> getDelegate() {
      return holder().getDelegate();
    }

    @Override
    public @Nullable ResourceKey<T> getKey() {
      return holder().getKey();
    }

    @Override
    public HolderLookup.@Nullable RegistryLookup<T> unwrapLookup() {
      return holder().unwrapLookup();
    }

    @Override
    public @Nullable ResourceKey<T> keyOrNull() {
      return holder().keyOrNull();
    }

    @Override
    public <A> @Nullable A getData(@NonNull DataMapType<T, A> type) {
      return holder().getData(type);
    }
  }
}
