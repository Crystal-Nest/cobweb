package it.crystalnest.cobweb;

import it.crystalnest.cobweb.api.pack.client.ClientPackExportCommand;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import org.jetbrains.annotations.ApiStatus;

/**
 * Registers client features on Fabric.
 */
@ApiStatus.Internal
public final class ClientModLoader implements ClientModInitializer {
  @Override
  public void onInitializeClient() {
    ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> ClientPackExportCommand.register(dispatcher, FabricClientCommandSource::sendFeedback, FabricClientCommandSource::sendError));
  }
}
