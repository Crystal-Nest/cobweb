package it.crystalnest.cobweb;

import it.crystalnest.cobweb.api.pack.command.PackExportCommand;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.ApiStatus;

/**
 * Registers client features on NeoForge.
 */
@ApiStatus.Internal
@Mod(value = Constants.MOD_ID, dist = Dist.CLIENT)
public final class ClientModLoader {
  /**
   * Registers the pack export command on the client event bus.
   */
  public ClientModLoader() {
    NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) -> PackExportCommand.registerClient(event.getDispatcher(), Minecraft.getInstance(), (source, message) -> source.sendSuccess(() -> message, false),
      CommandSourceStack::sendFailure));
  }
}
