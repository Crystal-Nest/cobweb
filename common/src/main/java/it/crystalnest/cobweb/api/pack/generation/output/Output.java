package it.crystalnest.cobweb.api.pack.generation.output;

import it.crystalnest.cobweb.api.pack.generation.lifecycle.Phase;
import it.crystalnest.cobweb.api.registry.CobwebDataPackRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Describes the latest phase and native lifetime of an output family.
 *
 * @param deadline latest compatible phase.
 * @param lifetime native loading lifetime.
 * @param registry native registry key when this contract describes registry entries.
 * @param directory directory when this contract describes a custom JSON resource family.
 */
public record Output(Phase deadline, ResourceLifetime lifetime, @Nullable ResourceKey<? extends Registry<?>> registry, @Nullable String directory) {
  /**
   * Recipes read by the native reloadable registry batch.
   */
  public static final Output RECIPES = registryEntries(Registries.RECIPE, ResourceLifetime.RELOADABLE);

  /**
   * Enchantments read by the native world registry batch.
   */
  public static final Output ENCHANTMENTS = registryEntries(Registries.ENCHANTMENT, ResourceLifetime.FULL_LOAD);

  /**
   * Loot tables read by the native reloadable registry batch.
   */
  public static final Output LOOT_TABLES = registryEntries(Registries.LOOT_TABLE, ResourceLifetime.RELOADABLE);

  /**
   * Advancements read by the native reloadable registry batch.
   */
  public static final Output ADVANCEMENTS = registryEntries(Registries.ADVANCEMENT, ResourceLifetime.RELOADABLE);

  /**
   * Files read during ordinary client asset loading.
   */
  public static final Output ASSETS = new Output(Phase.CLIENT_RESOURCES, ResourceLifetime.RELOADABLE, null);

  /**
   * Tags emitted before existing-registry tag preparation.
   */
  public static final Output TAGS = new Output(Phase.DATA_RESOURCES, ResourceLifetime.RELOADABLE, null);

  /**
   * Compressed structures read after native data resource application.
   */
  public static final Output STRUCTURES = new Output(Phase.RELOADABLE_REGISTRIES_READY, ResourceLifetime.RELOADABLE, null);

  /**
   * @param deadline latest compatible phase.
   * @param lifetime lifetime of the submitted files.
   * @param registry optional native registry key.
   * @param directory optional custom JSON resource directory.
   */
  public Output {
    Objects.requireNonNull(deadline);
    Objects.requireNonNull(lifetime);
  }

  /**
   * @param deadline latest compatible phase.
   * @param lifetime native loading lifetime.
   * @param registry native registry key, if applicable.
   */
  public Output(Phase deadline, ResourceLifetime lifetime, @Nullable ResourceKey<? extends Registry<?>> registry) {
    this(deadline, lifetime, registry, null);
  }

  /**
   * Returns the native output contract of a declared data pack registry.
   *
   * @param registry registry definition.
   * @return output contract for complete registry entries.
   */
  public static Output registryEntries(CobwebDataPackRegistry<?> registry) {
    return registryEntries(registry.key(), registry.lifetime() == CobwebDataPackRegistry.Lifetime.WORLD ? ResourceLifetime.FULL_LOAD : ResourceLifetime.RELOADABLE);
  }

  /**
   * Declares native world or reloadable registry output when the registry was registered outside Cobweb.
   *
   * @param key native registry key.
   * @param lifetime native registry lifetime.
   * @return output contract for complete entries.
   */
  public static Output registryEntries(ResourceKey<? extends Registry<?>> key, ResourceLifetime lifetime) {
    Objects.requireNonNull(key);
    if (lifetime == ResourceLifetime.RELOADABLE) {
      return new Output(Phase.WORLD_CONTEXT_READY, lifetime, key);
    }
    boolean dimension = RegistryDataLoader.DIMENSION_REGISTRIES.stream().anyMatch(data -> data.key().equals(key));
    return new Output(dimension ? Phase.WORLD_REGISTRIES_READY : Phase.STATIC_TAGS_READY, lifetime, key);
  }
}
