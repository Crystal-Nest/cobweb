package it.crystalnest.cobweb.platform;

import com.mojang.serialization.Codec;
import it.crystalnest.cobweb.api.pack.PackSources;
import it.crystalnest.cobweb.api.registry.CobwebDataPackRegistry;
import it.crystalnest.cobweb.api.registry.CobwebEntry;
import it.crystalnest.cobweb.api.registry.CobwebRegister;
import it.crystalnest.cobweb.api.registry.CobwebRegistry;
import it.crystalnest.cobweb.platform.services.RegistryHelper;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.fabricmc.fabric.api.event.registry.FabricRegistry;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.fabricmc.fabric.api.event.registry.RegistryAttribute;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PathPackResources;
import it.crystalnest.cobweb.platform.services.BundledPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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

  /**
   * Internal list of {@link Pack data pack}s to register.
   */
  private static final List<Supplier<Pack>> DATA_PACKS = new ArrayList<>();

  /**
   * Internal list of {@link Pack texture pack}s to register.
   */
  private static final List<Supplier<Pack>> ASSET_PACKS = new ArrayList<>();

  /**
   * Registers all Cobweb data packs with the given repository.
   *
   * @param repository {@link PackRepository}.
   * @return updated repository.
   */
  public static PackRepository registerDataPacks(PackRepository repository) {
    registerPacks(DATA_PACKS, repository);
    return repository;
  }

  /**
   * Registers all Cobweb asset packs with the given repository.
   *
   * @param repository {@link PackRepository}.
   * @return updated repository.
   */
  public static PackRepository registerTexturePacks(PackRepository repository) {
    registerPacks(ASSET_PACKS, repository);
    return repository;
  }

  /**
   * Registers the provided list of resource packs to the given repository.
   *
   * @param packs resource packs.
   * @param repository {@link PackRepository}.
   */
  private static void registerPacks(List<Supplier<Pack>> packs, PackRepository repository) {
    for (var definition : packs) {
      ((PackSources) repository).addSource(consumer -> {
        Pack pack = definition.get();
        if (pack != null) {
          consumer.accept(pack);
        }
      });
    }
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
  protected void addPackSource(PackType type, Supplier<Pack> supplier) {
    switch (type) {
      case SERVER_DATA -> DATA_PACKS.add(supplier);
      case CLIENT_RESOURCES -> ASSET_PACKS.add(supplier);
    }
  }

  @Override
  public String bundledKnownPackNamespace() {
    return "cobweb";
  }

  @Override
  @Nullable
  public BundledPackResources openBundledPack(PackLocationInfo location, String owner, String root) {
    return FabricLoader.getInstance().getModContainer(owner).map(container -> {
      var path = container.findPath(root).orElseGet(() -> container.getRootPaths().getFirst().resolve(root));
      return new BundledPackResources(new PathPackResources(location, path),
        (type, namespace, output) -> PathPackResources.listPath(namespace, path.resolve(type.getDirectory()).resolve(namespace), List.of(), output));
    }).orElse(null);
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
     * Creates a register backed by the native registry with the supplied key.
     *
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
       * Creates an item register for the supplied namespace.
       *
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
       * Creates a block register for the supplied namespace.
       *
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
      private Entities(String namespace) {
        super(Registries.ENTITY_TYPE, namespace);
      }
    }

    /**
     * Specialized register for data component types.
     */
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
