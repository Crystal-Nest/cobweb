package it.crystalnest.cobweb;

import it.crystalnest.cobweb.api.pack.query.PackExportCommand;
import it.crystalnest.cobweb.platform.NeoForgePackHelper;
import it.crystalnest.cobweb.platform.NeoForgeRegistryHelper;
import it.crystalnest.cobweb.platform.Services;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
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
   */
  public ModLoader(IEventBus bus) {
    ((NeoForgeRegistryHelper) Services.REGISTRY).register(bus);
    ((NeoForgePackHelper) Services.PACK).register(bus);
    CommonModLoader.init();
    NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> PackExportCommand.register(event.getDispatcher()));
  }
}
