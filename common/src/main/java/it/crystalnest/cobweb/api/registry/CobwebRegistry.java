package it.crystalnest.cobweb.api.registry;

import it.crystalnest.cobweb.platform.Services;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.alchemy.Potion;
import org.jetbrains.annotations.Nullable;

/**
 * Cobweb unified registering API.
 */
public final class CobwebRegistry {
  private CobwebRegistry() {}

  /**
   * Creates a Java-populated static registry with unsynced IDs and no default entry.
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
   * Immutable settings for a Java-populated static registry. Native defaults are used for other options.
   * @param synced whether native numeric registry IDs should be synchronized to clients.
   *               This does not transmit arbitrary entry definitions or Java behavior.
   * @param defaultKey optional fallback entry identifier; that entry must be registered separately.
   */
  public record Settings(boolean synced, @Nullable Identifier defaultKey) {
    /** Creates unsynced settings without a fallback entry. */
    public Settings() {
      this(false, null);
    }

    /**
     * Copies these settings with native ID synchronization enabled or disabled.
     * @param synced whether to synchronize IDs.
     * @return updated settings.
     */
    public Settings sync(boolean synced) {
      return new Settings(synced, defaultKey);
    }

    /**
     * Copies these settings with a fallback entry.
     * @param defaultKey full fallback identifier, or null for no fallback.
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
   * @param registryKey component-type registry key.
   * @param namespace mod ID.
   * @return specialized component register.
   */
  public static CobwebRegister.DataComponents ofDataComponents(ResourceKey<Registry<DataComponentType<?>>> registryKey, String namespace) {
    return Services.REGISTRY.ofDataComponents(registryKey, namespace);
  }

  /**
   * Provides a {@link CobwebRegister} of {@link MobEffect}s for the specified mod.
   *
   * @param namespace mod ID.
   * @return {@link CobwebRegister} of {@link MobEffect}s.
   */
  public static CobwebRegister<MobEffect> ofMobEffects(String namespace) {
    return of(Registries.MOB_EFFECT, namespace);
  }

  /**
   * Provides a {@link CobwebRegister} of {@link Potion}s for the specified mod.
   *
   * @param namespace mod ID.
   * @return {@link CobwebRegister} of {@link Potion}s.
   */
  public static CobwebRegister<Potion> ofPotions(String namespace) {
    return of(Registries.POTION, namespace);
  }

  /**
   * Provides a {@link CobwebRegister} of {@link CreativeModeTab}s for the specified mod.
   *
   * @param namespace mod ID.
   * @return {@link CobwebRegister} of {@link CreativeModeTab}s.
   */
  public static CobwebRegister<CreativeModeTab> ofCreativeModeTabs(String namespace) {
    return of(Registries.CREATIVE_MODE_TAB, namespace);
  }
}
