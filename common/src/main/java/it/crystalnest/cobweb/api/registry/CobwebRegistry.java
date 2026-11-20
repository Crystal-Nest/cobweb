package it.crystalnest.cobweb.api.registry;

import com.mojang.serialization.Codec;
import it.crystalnest.cobweb.platform.Services;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Cobweb unified registering API.
 */
public final class CobwebRegistry {
  private CobwebRegistry() {}

  /**
   * Creates a Java-populated static registry with unsynced IDs and no default entry.
   *
   * @param registryKey new registry key.
   * @param namespace namespace for this register's entries.
   * @param <R> value type.
   * @return register that owns creation of the registry.
   * @see #create(ResourceKey, String, Settings)
   */
  public static <R> CobwebRegister<R> create(ResourceKey<Registry<R>> registryKey, String namespace) {
    return create(registryKey, namespace, new Settings());
  }

  /**
   * Creates a Java-populated static registry using the loader's native builder.
   * Call once during mod initialization, before NeoForge's new-registry event or Fabric's registry freeze.
   * Other namespaces can contribute through {@link #of(ResourceKey, String)} after creation.
   * Duplicate creation fails; obtaining an existing register does not create a registry.
   * Fabric installs the registry immediately. NeoForge creates it now and installs it at its event.
   * This does not declare a JSON/data-pack registry.
   *
   * @param registryKey new registry key.
   * @param namespace namespace for this register's entries; independent of the registry key's namespace.
   * @param settings native ID synchronization and default-entry settings.
   * @param <R> value type.
   * @return register that owns creation of the registry.
   */
  public static <R> CobwebRegister<R> create(ResourceKey<Registry<R>> registryKey, String namespace, Settings settings) {
    return Services.REGISTRY.create(registryKey, namespace, settings);
  }

  /**
   * Declares a registry loaded from data packs when opening a world, without synchronizing its entries to clients.
   * Call during mod initialization. Use {@link #createWorldRegistry(ResourceKey, Codec, Codec)} to enable synchronization.
   *
   * @param key native registry key.
   * @param codec codec for complete entries in data packs.
   * @param <T> entry type.
   * @return registered world registry definition.
   * @throws IllegalStateException if declarations have closed or Cobweb already registered the key.
   */
  public static <T> CobwebDataPackRegistry<T> createWorldRegistry(ResourceKey<Registry<T>> key, Codec<T> codec) {
    return createDataPack(key, codec, CobwebDataPackRegistry.Lifetime.WORLD, null);
  }

  /**
   * Declares a world registry with native entry synchronization to clients. Call during mod initialization.
   * Pass the same codec twice to synchronize complete entries, or supply a network codec that omits fields unnecessary to clients.
   * The network codec encodes on the server and decodes on clients; it must not depend on client-only classes.
   * It must also decode complete data-pack JSON when Minecraft loads an entry from a matching known pack on the client.
   * Clients must support the registry and its network codec.
   *
   * @param key native registry key.
   * @param codec codec for complete entries in data packs.
   * @param networkCodec non-null codec enabling entry synchronization; may be the same as {@code codec}.
   * @param <T> entry type.
   * @return registered world registry definition.
   * @throws IllegalStateException if declarations have closed or Cobweb already registered the key.
   */
  public static <T> CobwebDataPackRegistry<T> createWorldRegistry(ResourceKey<Registry<T>> key, Codec<T> codec, Codec<T> networkCodec) {
    return createDataPack(key, codec, CobwebDataPackRegistry.Lifetime.WORLD, Objects.requireNonNull(networkCodec));
  }

  /**
   * Declares a registry loaded from data packs when opening or reloading a world, without synchronizing its entries to clients.
   * Call during mod initialization.
   * <p>
   * Custom reloadable registries load after Minecraft's loot registries on this version.
   * Their codecs can reference loot entries and other custom registries in the same batch.
   * Loot codecs cannot reference custom reloadable entries during decoding.
   *
   * @param key native registry key.
   * @param codec codec for complete entries in data packs.
   * @param <T> entry type.
   * @return registered reloadable registry definition.
   * @throws IllegalStateException if declarations have closed or Cobweb already registered the key.
   */
  public static <T> CobwebDataPackRegistry<T> createReloadableRegistry(ResourceKey<Registry<T>> key, Codec<T> codec) {
    return createDataPack(key, codec, CobwebDataPackRegistry.Lifetime.RELOADABLE, null);
  }

  /**
   * Creates a data pack registry definition and declares it through the current loader.
   *
   * @param key native registry key.
   * @param codec codec for complete entries in data packs.
   * @param lifetime when Minecraft loads the registry's entries.
   * @param networkCodec synchronization codec for a world registry, or {@code null} to leave entries unsynchronized.
   * @param <T> entry type.
   * @return registered data pack registry definition.
   * @throws IllegalStateException if declarations have closed or Cobweb already registered the key.
   * @throws NullPointerException if the registry key or entry codec is {@code null}.
   */
  private static <T> CobwebDataPackRegistry<T> createDataPack(ResourceKey<Registry<T>> key, Codec<T> codec, CobwebDataPackRegistry.Lifetime lifetime, @Nullable Codec<T> networkCodec) {
    var definition = new CobwebDataPackRegistry<>(key, codec, lifetime);
    Services.REGISTRY.registerDataPackRegistry(definition, networkCodec);
    return definition;
  }

  /**
   * Immutable settings for a Java-populated static registry. Native defaults are used for other options.
   * The no-argument constructor creates unsynced settings without a fallback entry.
   *
   * @param synced whether native numeric registry IDs should be synchronized to clients.
   * This does not transmit arbitrary entry definitions or Java behavior.
   * @param defaultKey optional fallback entry identifier; that entry must be registered separately.
   */
  public record Settings(boolean synced, @Nullable Identifier defaultKey) {
    public Settings() {
      this(false, null);
    }

    /**
     * Copies these settings with native ID synchronization enabled or disabled.
     *
     * @param synced whether to synchronize IDs.
     * @return updated settings.
     */
    public Settings sync(boolean synced) {
      return new Settings(synced, defaultKey);
    }

    /**
     * Copies these settings with a fallback entry.
     *
     * @param defaultKey full fallback identifier, or {@code null} for no fallback.
     * @return updated settings.
     */
    public Settings defaultKey(@Nullable Identifier defaultKey) {
      return new Settings(synced, defaultKey);
    }
  }

  /**
   * Provides a {@link CobwebRegister} for the specified mod and {@link Registry}.
   *
   * @param registry Minecraft {@link Registry}.
   * @param namespace mod ID.
   * @param <R> register type.
   * @return {@link CobwebRegister}.
   */
  public static <R> CobwebRegister<R> of(Registry<R> registry, String namespace) {
    return Services.REGISTRY.of(registry, namespace);
  }

  /**
   * Provides a {@link CobwebRegister} for the specified mod and {@link Registry}.
   *
   * @param registryKey Minecraft {@link Registry} key.
   * @param namespace mod ID.
   * @param <R> register type.
   * @return {@link CobwebRegister}.
   */
  public static <R> CobwebRegister<R> of(ResourceKey<? extends Registry<R>> registryKey, String namespace) {
    return Services.REGISTRY.of(registryKey, namespace);
  }

  /**
   * Provides a {@link CobwebRegister.Items} for the specified mod.
   *
   * @param namespace mod ID.
   * @return {@link CobwebRegister.Items}.
   */
  public static CobwebRegister.Items ofItems(String namespace) {
    return Services.REGISTRY.ofItems(namespace);
  }

  /**
   * Provides a {@link CobwebRegister.Blocks} for the specified mod.
   *
   * @param namespace mod ID.
   * @return {@link CobwebRegister.Blocks}.
   */
  public static CobwebRegister.Blocks ofBlocks(String namespace) {
    return Services.REGISTRY.ofBlocks(namespace);
  }

  /**
   * Provides a specialized entity type register for the specified mod.
   *
   * @param namespace mod ID.
   * @return {@link CobwebRegister.Entities}.
   */
  public static CobwebRegister.Entities ofEntityTypes(String namespace) {
    return Services.REGISTRY.ofEntityTypes(namespace);
  }

  /**
   * Provides a specialized register for the standard data component registry.
   *
   * @param namespace mod ID.
   * @return {@link CobwebRegister.DataComponents}.
   */
  public static CobwebRegister.DataComponents ofDataComponents(String namespace) {
    return Services.REGISTRY.ofDataComponents(namespace);
  }

  /**
   * Provides builder conveniences for an explicitly selected component-type registry, such as
   * {@link Registries#ENCHANTMENT_EFFECT_COMPONENT_TYPE} or a Java-created custom registry.
   * Custom keys may return a specialized view of an already obtained generic register;
   * both share the native register, aliases and declared-entry view.
   *
   * @param registryKey component-type registry key.
   * @param namespace mod ID.
   * @return specialized component register.
   */
  public static CobwebRegister.DataComponents ofDataComponents(ResourceKey<Registry<DataComponentType<?>>> registryKey, String namespace) {
    return Services.REGISTRY.ofDataComponents(registryKey, namespace);
  }

  /**
   * Provides the specialized potion register for the specified mod.
   *
   * @param namespace mod ID.
   * @return specialized potion register.
   */
  public static CobwebRegister.Potions ofPotions(String namespace) {
    return Services.REGISTRY.ofPotions(namespace);
  }

  /**
   * Provides the specialized creative mode tab register for the specified mod.
   *
   * @param namespace mod ID.
   * @return specialized creative mode tab register.
   */
  public static CobwebRegister.CreativeModeTabs ofCreativeModeTabs(String namespace) {
    return Services.REGISTRY.ofCreativeModeTabs(namespace);
  }
}
