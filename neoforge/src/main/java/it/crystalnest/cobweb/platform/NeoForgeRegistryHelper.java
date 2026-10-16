package it.crystalnest.cobweb.platform;

import it.crystalnest.cobweb.api.pack.fixed.StaticResourcePack;
import it.crystalnest.cobweb.api.registry.CobwebEntry;
import it.crystalnest.cobweb.api.registry.CobwebRegister;
import it.crystalnest.cobweb.api.registry.CobwebRegistry;
import it.crystalnest.cobweb.platform.services.RegistryHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.function.Supplier;

/**
 * NeoForge registration helper.
 */
public final class NeoForgeRegistryHelper extends RegistryHelper<NeoForgeRegistryHelper.Register<?>> {
  /**
   * NeoForge event bus.
   */
  private IEventBus bus = null;

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
  @SuppressWarnings("unchecked")
  public <R> Register<R> of(ResourceKey<? extends Registry<R>> registryKey, String namespace) {
    return (Register<R>) registries.computeIfAbsent(namespace, key -> new HashMap<>()).computeIfAbsent(registryKey.identifier(), key -> {
      Register<?> register;
      if (registryKey.equals(Registries.ITEM)) {
        register = new Register.Items(namespace);
      } else if (registryKey.equals(Registries.BLOCK)) {
        register = new Register.Blocks(namespace);
      } else if (registryKey.equals(Registries.ENTITY_TYPE)) {
        register = new Register.Entities(namespace);
      } else if (registryKey.equals(Registries.DATA_COMPONENT_TYPE) || registryKey.equals(Registries.ENCHANTMENT_EFFECT_COMPONENT_TYPE)) {
        register = new Register.DataComponents(registryKey.equals(Registries.DATA_COMPONENT_TYPE) ? Registries.DATA_COMPONENT_TYPE : Registries.ENCHANTMENT_EFFECT_COMPONENT_TYPE, namespace);
      } else {
        register = new Register<>(registryKey, namespace);
      }
      register.register(bus);
      return register;
    });
  }

  @Override
  public void registerDynamicResourcePack(PackType type, Supplier<Pack> supplier) {
    bus.addListener((AddPackFindersEvent event) -> {
      if (event.getPackType() == type) {
        event.addRepositorySource(consumer -> consumer.accept(supplier.get()));
      }
    });
  }

  @Override
  public void registerStaticResourcePack(StaticResourcePack pack) {
    bus.addListener((AddPackFindersEvent event) -> event.addPackFinders(pack.identifier(), pack.type(), pack.name(), pack.source(), pack.alwaysActive(), pack.position()));
  }

  /**
   * Saves the given event bus to register game objects.
   *
   * @param bus {@link IEventBus}.
   */
  public void register(IEventBus bus) {
    this.bus = bus;
  }

  /**
   * Extension for {@link DeferredRegister} to implement {@link CobwebRegister}.
   *
   * @param <R> registry type.
   */
  public static class Register<R> implements CobwebRegister<R> {
    /** Declared handles; native registration timing is unaffected. */
    private final List<CobwebEntry<? extends R>> entries = new ArrayList<>();

    /** Unmodifiable live view, avoiding a new copy on each inspection. */
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
       * @param namespace {@link DeferredRegister#namespace}.
       */
      private Blocks(String namespace) {
        super(Registries.BLOCK, namespace);
      }
    }

    /** Specialized register for entity types. */
    public static final class Entities extends Register<EntityType<?>> implements CobwebRegister.Entities {
      private Entities(String namespace) {
        super(Registries.ENTITY_TYPE, namespace);
      }
    }

    /** Specialized register for data component types. */
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
    public Holder<T> getDelegate() {
      return holder().getDelegate();
    }

    @Override
    @SuppressWarnings("removal")
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
    public <A> @Nullable A getData(DataMapType<T, A> type) {
      return holder().getData(type);
    }

    @Override
    public boolean equals(Object other) {
      return this == other || holder().equals(other instanceof CobwebEntry<?> entry ? entry.holder() : other);
    }
  }
}
