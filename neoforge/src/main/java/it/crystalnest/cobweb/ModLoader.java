package it.crystalnest.cobweb;

import it.crystalnest.cobweb.api.pack.command.PackExportCommand;
import it.crystalnest.cobweb.platform.NeoForgePackHelper;
import it.crystalnest.cobweb.platform.NeoForgeRegistryHelper;
import it.crystalnest.cobweb.platform.Services;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
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
   * @param bus Event bus.
   * @param mod Cobweb's mod container.
   */
  public ModLoader(IEventBus bus, ModContainer mod) {
    String version = mod.getModInfo().getVersion().toString();
    String side = FMLEnvironment.getDist().name();
    CommonModLoader.logSetup("common setup started", version, "NeoForge", side);
    ((NeoForgeRegistryHelper) Services.REGISTRY).register(bus);
    ((NeoForgePackHelper) Services.PACK).register(bus);
    CommonModLoader.logSetup("registration services ready", version, "NeoForge", side);
    NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> PackExportCommand.registerServer(event.getDispatcher()));
    CommonModLoader.logSetup("common setup completed", version, "NeoForge", side);
  }
}
