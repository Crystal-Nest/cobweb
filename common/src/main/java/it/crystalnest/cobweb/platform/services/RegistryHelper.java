package it.crystalnest.cobweb.platform.services;

import com.mojang.serialization.Codec;
import it.crystalnest.cobweb.api.registry.CobwebDataPackRegistry;
import it.crystalnest.cobweb.api.registry.CobwebEntry;
import it.crystalnest.cobweb.api.registry.CobwebRegister;
import it.crystalnest.cobweb.api.registry.CobwebRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Registers game objects and declares registries through the current loader.
 *
 * @param <T> actual register type.
 */
public abstract class RegistryHelper<T extends CobwebRegister<?>> {
  /**
   * Map linking all mods that use the API to all the registries they use.<br>
   * Used to avoid creating and registering multiple instances of the same register.
   */
  protected final Map<String, Map<Identifier, T>> registries = new ConcurrentHashMap<>();

  /**
   * Keys created through this service, including registries not yet installed on NeoForge.
   */
  private final Set<Identifier> createdRegistries = new HashSet<>();

  /**
   * Whether the loader's declaration window has ended.
   */
  private boolean dataPackRegistriesClosed;

  /**
   * Specialized views for custom component registries, sharing the canonical native register.
   */
  private final Map<String, Map<Identifier, CobwebRegister.DataComponents>> componentRegisters = new ConcurrentHashMap<>();

  /**
   * Creates a static registry once, preserving native installation timing.
   *
   * @param registryKey new registry key.
   * @param namespace entry namespace.
   * @param settings native builder settings.
   * @param <R> value type.
   * @return creating register.
   */
  public final synchronized <R> CobwebRegister<R> create(ResourceKey<Registry<R>> registryKey, String namespace, CobwebRegistry.Settings settings) {
    Objects.requireNonNull(settings);
    if (createdRegistries.contains(registryKey.identifier())) {
      throw new IllegalStateException("Registry already created: " + registryKey.identifier());
    }
    CobwebRegister<R> register = createRegistry(registryKey, namespace, settings);
    createdRegistries.add(registryKey.identifier());
    return register;
  }

  /**
   * Creates a registry using the native builder and obtains its register.
   *
   * @param registryKey new registry key.
   * @param namespace entry namespace.
   * @param settings native builder settings.
   * @param <R> value type.
   * @return creating register.
   */
  protected abstract <R> CobwebRegister<R> createRegistry(ResourceKey<Registry<R>> registryKey, String namespace, CobwebRegistry.Settings settings);

  /**
   * Reserves the registry key and delegates its declaration to the loader.
   *
   * @param definition registry key, codec and loading lifetime.
   * @param networkCodec optional synchronization codec for a world registry.
   * @param <E> entry type.
   */
  public final synchronized <E> void registerDataPackRegistry(CobwebDataPackRegistry<E> definition, @Nullable Codec<E> networkCodec) {
    Identifier id = definition.key().identifier();
    if (dataPackRegistriesClosed) {
      throw new IllegalStateException("Data pack registry declarations have closed: " + id);
    }
    if (createdRegistries.contains(id)) {
      throw new IllegalStateException("Registry already created: " + id);
    }
    addDataPackRegistry(definition, networkCodec);
    createdRegistries.add(id);
  }

  /**
   * Ends the loader's data pack registry declaration window.
   */
  public final synchronized void closeDataPackRegistries() {
    dataPackRegistriesClosed = true;
  }

  /**
   * Declares a registry through the loader's data pack registry API.
   *
   * @param definition registry definition.
   * @param networkCodec optional synchronization codec for a world registry.
   * @param <E> entry type.
   */
  protected abstract <E> void addDataPackRegistry(CobwebDataPackRegistry<E> definition, @Nullable Codec<E> networkCodec);

  /**
   * Returns the native registry declarations read during a full world load.
   *
   * @return native world registry definitions.
   */
  public abstract List<RegistryDataLoader.RegistryData<?>> worldRegistries();

  /**
   * Returns the native registry declarations read during a reload.
   *
   * @return native reloadable registry definitions.
   */
  public abstract List<RegistryDataLoader.RegistryData<?>> reloadableRegistries();

  /**
   * Provides a {@link CobwebRegister} for the specified mod and {@link Registry}.
   *
   * @param registry Minecraft {@link Registry}.
   * @param namespace mod ID.
   * @param <R> register type.
   * @return {@link CobwebRegister}.
   */
  public <R> CobwebRegister<R> of(Registry<R> registry, String namespace) {
    return of(registry.key(), namespace);
  }

  /**
   * Provides a {@link CobwebRegister} for the specified mod and {@link Registry}.
   *
   * @param registryKey Minecraft {@link Registry} key.
   * @param namespace mod ID.
   * @param <R> register type.
   * @return {@link CobwebRegister}.
   */
  public abstract <R> CobwebRegister<R> of(ResourceKey<? extends Registry<R>> registryKey, String namespace);

  /**
   * Provides a {@link CobwebRegister.Items} for the specified mod.
   *
   * @param namespace mod ID.
   * @return {@link CobwebRegister.Items}.
   */
  public CobwebRegister.Items ofItems(String namespace) {
    return (CobwebRegister.Items) of(Registries.ITEM, namespace);
  }

  /**
   * Provides a {@link CobwebRegister.Blocks} for the specified mod.
   *
   * @param namespace mod ID.
   * @return {@link CobwebRegister.Blocks}.
   */
  public CobwebRegister.Blocks ofBlocks(String namespace) {
    return (CobwebRegister.Blocks) of(Registries.BLOCK, namespace);
  }

  /**
   * Provides the specialized entity type register.
   *
   * @param namespace mod ID.
   * @return specialized entity type register.
   */
  public CobwebRegister.Entities ofEntityTypes(String namespace) {
    return (CobwebRegister.Entities) of(Registries.ENTITY_TYPE, namespace);
  }

  /**
   * Provides the specialized standard data component register.
   *
   * @param namespace mod ID.
   * @return specialized data component register.
   */
  public CobwebRegister.DataComponents ofDataComponents(String namespace) {
    return ofDataComponents(Registries.DATA_COMPONENT_TYPE, namespace);
  }

  /**
   * Obtains a specialized component register, sharing registrations with generic access.
   *
   * @param registryKey component-type registry key.
   * @param namespace mod ID.
   * @return specialized register or a cached view over the native register.
   */
  public CobwebRegister.DataComponents ofDataComponents(ResourceKey<Registry<DataComponentType<?>>> registryKey, String namespace) {
    CobwebRegister<DataComponentType<?>> register = of(registryKey, namespace);
    if (register instanceof CobwebRegister.DataComponents components) {
      return components;
    }
    return componentRegisters.computeIfAbsent(namespace, key -> new ConcurrentHashMap<>()).computeIfAbsent(registryKey.identifier(), key -> new ComponentRegister(register));
  }

  /**
   * Provides the specialized potion register.
   *
   * @param namespace mod ID.
   * @return specialized potion register.
   */
  public CobwebRegister.Potions ofPotions(String namespace) {
    return (CobwebRegister.Potions) of(Registries.POTION, namespace);
  }

  /**
   * Provides the specialized creative mode tab register.
   *
   * @param namespace mod ID.
   * @return specialized creative mode tab register.
   */
  public CobwebRegister.CreativeModeTabs ofCreativeModeTabs(String namespace) {
    return (CobwebRegister.CreativeModeTabs) of(Registries.CREATIVE_MODE_TAB, namespace);
  }

  /**
   * Creates a fresh creative mode tab builder using the loader's tab placement behavior.
   *
   * @return native creative mode tab builder.
   */
  public abstract CreativeModeTab.Builder createCreativeModeTabBuilder();

  /**
   * A builder-convenience view; does not subscribe or queue another native register.
   */
  private record ComponentRegister(CobwebRegister<DataComponentType<?>> delegate) implements CobwebRegister.DataComponents {
    @Override
    public String namespace() {
      return delegate.namespace();
    }

    @Override
    public ResourceKey<? extends Registry<DataComponentType<?>>> registryKey() {
      return delegate.registryKey();
    }

    @Override
    public Registry<DataComponentType<?>> registry() {
      return delegate.registry();
    }

    @Override
    public void addAlias(Identifier from, Identifier to) {
      delegate.addAlias(from, to);
    }

    @Override
    public Collection<CobwebEntry<? extends DataComponentType<?>>> entries() {
      return delegate.entries();
    }

    @Override
    public <D extends DataComponentType<?>> CobwebEntry<D> register(String name, Supplier<? extends D> supplier) {
      return delegate.register(name, supplier);
    }
  }
}
