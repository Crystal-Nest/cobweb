package it.crystalnest.cobweb;

import it.crystalnest.cobweb.platform.NeoForgeConfigHelper;
import it.crystalnest.cobweb.platform.NeoForgeRegistryHelper;
import it.crystalnest.cobweb.platform.Services;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.jetbrains.annotations.ApiStatus;

/**
 * Mod loader.
 */
@ApiStatus.Internal
@Mod(Constants.MOD_ID)
public final class ModLoader {
  /**
   * Mod initialization.
   *
   * @param bus Event bus.
   * @param container Mod container.
   */
  public ModLoader(IEventBus bus, ModContainer container) {
    CommonModLoader.init();
    ((NeoForgeRegistryHelper) Services.REGISTRY).register(bus);
    if (Services.CONFIG != null) {
      ((NeoForgeConfigHelper) Services.CONFIG).register(container);
    }
  }
}
