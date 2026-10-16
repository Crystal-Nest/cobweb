package it.crystalnest.cobweb.platform;

import it.crystalnest.cobweb.api.pack.PackSources;
import it.crystalnest.cobweb.api.pack.fixed.StaticResourcePack;
import it.crystalnest.cobweb.api.registry.CobwebEntry;
import it.crystalnest.cobweb.api.registry.CobwebRegister;
import it.crystalnest.cobweb.api.registry.CobwebRegistry;
import it.crystalnest.cobweb.platform.services.RegistryHelper;
import net.fabricmc.fabric.api.event.registry.FabricRegistry;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.fabricmc.fabric.api.event.registry.RegistryAttribute;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Fabric registration helper.
 */
public final class FabricRegistryHelper extends RegistryHelper<FabricRegistryHelper.DeferredRegister<?>> {
  /**
   * Internal list of {@link Pack data pack}s to register.
   */
  private static final List<Supplier<Pack>> DYNAMIC_DATA_PACKS = new ArrayList<>();

  /**
   * Internal list of {@link Pack texture pack}s to register.
   */
  private static final List<Supplier<Pack>> DYNAMIC_TEXTURE_PACKS = new ArrayList<>();

  /**
   * Internal list of {@link StaticResourcePack} of type {@link PackType#SERVER_DATA}.
   */
  private static final List<StaticResourcePack> STATIC_DATA_PACKS = new ArrayList<>();

  /**
   * Internal list of {@link StaticResourcePack} of type {@link PackType#CLIENT_RESOURCES}.
   */
  private static final List<StaticResourcePack> STATIC_TEXTURE_PACKS = new ArrayList<>();

  /**
   * Registers all data packs, dynamic and static, to the given repository.
   *
   * @param repository {@link PackRepository}.
   * @return updated repository.
   */
  public static PackRepository registerDataPacks(PackRepository repository) {
    registerDynamicPacks(DYNAMIC_DATA_PACKS, repository);
    registerStaticPacks(STATIC_DATA_PACKS, repository);
    return repository;
  }

  /**
   * Registers all texture packs, dynamic and static, to the given repository.
   *
   * @param repository {@link PackRepository}.
   * @return updated repository.
   */
  public static PackRepository registerTexturePacks(PackRepository repository) {
    registerDynamicPacks(DYNAMIC_TEXTURE_PACKS, repository);
    registerStaticPacks(STATIC_TEXTURE_PACKS, repository);
    return repository;
  }

  /**
   * Registers the provided list of dynamic resource packs to the given repository.
   *
   * @param packs list of dynamic packs.
   * @param repository {@link PackRepository} to update.
   */
  private static void registerDynamicPacks(List<Supplier<Pack>> packs, PackRepository repository) {
    registerPacks(packs, Supplier::get, repository);
  }

  /**
   * Registers the provided list of static resource packs to the given repository.
   *
   * @param packs list of static packs.
   * @param repository {@link PackRepository} to update.
   */
  private static void registerStaticPacks(List<StaticResourcePack> packs, PackRepository repository) {
    registerPacks(packs, StaticResourcePack::toPack, repository);
  }

  /**
   * Registers the provided list of resource packs to the given repository.
   *
   * @param packs resource packs.
   * @param toPack function to map a resource pack into a {@link Pack}.
   * @param repository {@link PackRepository}.
   * @param <T> resource pack type.
   */
  private static <T> void registerPacks(List<T> packs, Function<T, Pack> toPack, PackRepository repository) {
    packs.stream().map(toPack).forEach(pack -> ((PackSources) repository).addSource(packConsumer -> packConsumer.accept(pack)));
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
  @SuppressWarnings("unchecked")
  public <R> DeferredRegister<R> of(ResourceKey<? extends Registry<R>> registryKey, String namespace) {
    return (DeferredRegister<R>) registries.computeIfAbsent(namespace, key -> new HashMap<>()).computeIfAbsent(registryKey.identifier(), key -> {
      DeferredRegister<?> register;
      if (registryKey.equals(Registries.ITEM)) {
        register = new DeferredRegister.Items(namespace);
      } else if (registryKey.equals(Registries.BLOCK)) {
        register = new DeferredRegister.Blocks(namespace);
      } else if (registryKey.equals(Registries.ENTITY_TYPE)) {
        register = new DeferredRegister.Entities(namespace);
      } else if (registryKey.equals(Registries.DATA_COMPONENT_TYPE) || registryKey.equals(Registries.ENCHANTMENT_EFFECT_COMPONENT_TYPE)) {
        register = new DeferredRegister.DataComponents(registryKey.equals(Registries.DATA_COMPONENT_TYPE) ? Registries.DATA_COMPONENT_TYPE : Registries.ENCHANTMENT_EFFECT_COMPONENT_TYPE, namespace);
      } else {
        register = new DeferredRegister<>(registryKey, namespace);
      }
      return register;
    });
  }

  @Override
  public void registerDynamicResourcePack(PackType type, Supplier<Pack> supplier) {
    switch (type) {
      case SERVER_DATA -> DYNAMIC_DATA_PACKS.add(supplier);
      case CLIENT_RESOURCES -> DYNAMIC_TEXTURE_PACKS.add(supplier);
    }
  }

  @Override
  public void registerStaticResourcePack(StaticResourcePack pack) {
    switch (pack.type()) {
      case SERVER_DATA -> STATIC_DATA_PACKS.add(pack);
      case CLIENT_RESOURCES -> STATIC_TEXTURE_PACKS.add(pack);
    }
  }

  /**
   * Deferred register.
   *
   * @param <R> registry type.
   */
  public static class DeferredRegister<R> implements CobwebRegister<R> {
    /** Declared handles; native registration timing is unaffected. */
    private final List<CobwebEntry<? extends R>> entries = new ArrayList<>();

    /** Unmodifiable live view, avoiding a new copy on each inspection. */
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
      ((FabricRegistry) registry).addAlias(from, to);
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

    /** Specialized register for entity types. */
    public static final class Entities extends DeferredRegister<EntityType<?>> implements CobwebRegister.Entities {
      private Entities(String namespace) {
        super(Registries.ENTITY_TYPE, namespace);
      }
    }

    /** Specialized register for data component types. */
    public static final class DataComponents extends DeferredRegister<DataComponentType<?>> implements CobwebRegister.DataComponents {
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
    private Entry(Holder.Reference<T> holder) {
      super(holder);
    }
  }
}
