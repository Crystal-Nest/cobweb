package it.crystalnest.cobweb.api.pack;

import it.crystalnest.cobweb.api.registry.CobwebDataPackRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;

import java.util.Objects;

/**
 * Describes an input used to select a generation phase.
 */
public sealed interface Input permits CapturedInput, Input.Native {
  /**
   * Completed recipe sources before native recipe decoding.
   */
  Input COMPLETE_RECIPES = new Native(Phase.WORLD_CONTEXT_READY, Phase.WORLD_CONTEXT_READY, true);

  /**
   * Completed asset sources before native client preparation.
   */
  Input COMPLETE_ASSETS = new Native(Phase.CLIENT_RESOURCES, Phase.CLIENT_RESOURCES, true);

  /**
   * Prepared static tag membership and native references on full loads.
   */
  Input STATIC_TAGS = new Native(Phase.STATIC_TAGS_READY, Phase.WORLD_CONTEXT_READY, false);

  /**
   * World and dimension context with prepared existing-registry tags.
   */
  Input WORLD_CONTEXT = new Native(Phase.WORLD_CONTEXT_READY, Phase.WORLD_CONTEXT_READY, false);

  /**
   * Native reloadable registry lookups after that registry batch completes.
   */
  Input RELOADABLE_REGISTRIES = new Native(Phase.RELOADABLE_REGISTRIES_READY, Phase.RELOADABLE_REGISTRIES_READY, false);

  /**
   * Returns the earliest phase that provides the native capability.
   *
   * @return earliest phase; captured values are available from the start on either side.
   */
  Phase readiness();

  /**
   * Returns the input contract for an existing Cobweb data pack registry.
   *
   * @param registry registered definition.
   * @return input contract for that registry's native lookup.
   */
  static Input registry(CobwebDataPackRegistry<?> registry) {
    return registry(registry.key(), registry.lifetime());
  }

  /**
   * Declares lookup of a native data pack registry registered outside Cobweb.
   *
   * @param key registry key.
   * @param lifetime native lifetime.
   * @return input contract for the registry lookup.
   */
  static Input registry(ResourceKey<? extends Registry<?>> key, CobwebDataPackRegistry.Lifetime lifetime) {
    Objects.requireNonNull(key);
    if (Objects.requireNonNull(lifetime) == CobwebDataPackRegistry.Lifetime.RELOADABLE) {
      return RELOADABLE_REGISTRIES;
    }
    boolean dimension = RegistryDataLoader.DIMENSION_REGISTRIES.stream().anyMatch(data -> data.key().equals(key));
    return new Native(dimension ? Phase.WORLD_CONTEXT_READY : Phase.WORLD_REGISTRIES_READY, Phase.DATA_RESOURCES, false);
  }

  /**
   * Defines a capability available from a native phase.
   *
   * @param readiness earliest compatible phase in a full load.
   * @param reloadReadiness earliest compatible phase in a reload.
   * @param completeSource whether the callback requires the completed generation stage.
   */
  record Native(Phase readiness, Phase reloadReadiness, boolean completeSource) implements Input {}
}
