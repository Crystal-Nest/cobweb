package it.crystalnest.cobweb.platform.services;

import it.crystalnest.cobweb.api.pack.dynamic.DynamicResourcePack;
import it.crystalnest.cobweb.api.pack.fixed.StaticResourcePack;
import it.crystalnest.cobweb.api.registry.CobwebEntry;
import it.crystalnest.cobweb.api.registry.CobwebRegister;
import it.crystalnest.cobweb.api.registry.CobwebRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Platform specific registration helper.
 *
 * @param <T> actual register type.
 */
public abstract class RegistryHelper<T extends CobwebRegister<?>> {
  /**
   * Map linking all mods that use the API to all the registries they use.<br>
   * Used to avoid creating and registering multiple instances of the same register.
   */
  protected final Map<String, Map<Identifier, T>> registries = new HashMap<>();

  /** Keys created through this service, including registries not yet installed on NeoForge. */
  private final Set<Identifier> createdRegistries = new HashSet<>();

  /** Specialized views for custom component registries, sharing the canonical native register. */
  private final Map<String, Map<Identifier, CobwebRegister.DataComponents>> componentRegisters = new HashMap<>();

  /**
   * Creates a static registry once, preserving native installation timing.
   * @param registryKey new registry key.
   * @param namespace entry namespace.
   * @param settings native builder settings.
   * @param <R> value type.
   * @return creating register.
   */
  public final <R> CobwebRegister<R> create(ResourceKey<Registry<R>> registryKey, String namespace, CobwebRegistry.Settings settings) {
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
   * @param registryKey new registry key.
   * @param namespace entry namespace.
   * @param settings native builder settings.
   * @param <R> value type.
   * @return creating register.
   */
  protected abstract <R> CobwebRegister<R> createRegistry(ResourceKey<Registry<R>> registryKey, String namespace, CobwebRegistry.Settings settings);

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
   * @param namespace mod ID.
   * @return specialized entity type register.
   */
  public CobwebRegister.Entities ofEntityTypes(String namespace) {
    return (CobwebRegister.Entities) of(Registries.ENTITY_TYPE, namespace);
  }

  /**
   * Provides the specialized standard data component register.
   * @param namespace mod ID.
   * @return specialized data component register.
   */
  public CobwebRegister.DataComponents ofDataComponents(String namespace) {
    return ofDataComponents(Registries.DATA_COMPONENT_TYPE, namespace);
  }

  /**
   * Obtains a specialized component register, sharing registrations with generic access.
   * @param registryKey component-type registry key.
   * @param namespace mod ID.
   * @return specialized register or a cached view over the native register.
   */
  public CobwebRegister.DataComponents ofDataComponents(ResourceKey<Registry<DataComponentType<?>>> registryKey, String namespace) {
    CobwebRegister<DataComponentType<?>> register = of(registryKey, namespace);
    if (register instanceof CobwebRegister.DataComponents components) {
      return components;
    }
    return componentRegisters.computeIfAbsent(namespace, key -> new HashMap<>())
      .computeIfAbsent(registryKey.identifier(), key -> new ComponentRegister(register));
  }

  /** A builder-convenience view; does not subscribe or queue another native register. */
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

  /**
   * Registers a {@link DynamicResourcePack}.
   *
   * @param type {@link PackType}.
   * @param supplier {@link Supplier} for the {@link Pack} to register.
   */
  public abstract void registerDynamicResourcePack(PackType type, Supplier<Pack> supplier);

  /**
   * Registers a {@link StaticResourcePack}.
   *
   * @param pack {@link StaticResourcePack}.
   */
  public abstract void registerStaticResourcePack(StaticResourcePack pack);
}
