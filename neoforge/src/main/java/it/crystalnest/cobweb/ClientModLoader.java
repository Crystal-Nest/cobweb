package it.crystalnest.cobweb;

import it.crystalnest.cobweb.api.pack.command.PackExportCommand;
import it.crystalnest.cobweb.platform.model.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.ApiStatus;

/**
 * Registers the client pack export command on the NeoForge client event bus.
 */
@ApiStatus.Internal
@Mod(value = Constants.MOD_ID, dist = Dist.CLIENT)
public final class ClientModLoader {
  /**
   * @param mod Cobweb's mod container.
   */
  public ClientModLoader(ModContainer mod) {
    String version = mod.getModInfo().getVersion().toString();
    CommonModLoader.logSetup(CommonModLoader.CLIENT_SETUP_STARTED, version, Platform.NEOFORGE.toString(), Dist.CLIENT.name());
    NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) -> PackExportCommand.registerClient(event.getDispatcher(), Minecraft.getInstance(), (source, message) -> source.sendSuccess(() -> message, false),
      CommandSourceStack::sendFailure));
    CommonModLoader.logSetup(CommonModLoader.CLIENT_SETUP_COMPLETED, version, Platform.NEOFORGE.toString(), Dist.CLIENT.name());
  }
}
