package it.crystalnest.cobweb;

import it.crystalnest.cobweb.api.pack.command.PackExportCommand;
import it.crystalnest.cobweb.platform.model.Platform;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.ApiStatus;

/**
 * Registers client features on Fabric.
 */
@ApiStatus.Internal
public final class ClientModLoader implements ClientModInitializer {
  @Override
  public void onInitializeClient() {
    String version = ModLoader.version(FabricLoader.getInstance());
    CommonModLoader.logSetup(CommonModLoader.CLIENT_SETUP_STARTED, version, Platform.FABRIC.toString(), EnvType.CLIENT.name());
    ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> PackExportCommand.registerClient(dispatcher, Minecraft.getInstance(), FabricClientCommandSource::sendFeedback, FabricClientCommandSource::sendError));
    CommonModLoader.logSetup(CommonModLoader.CLIENT_SETUP_COMPLETED, version, Platform.FABRIC.toString(), EnvType.CLIENT.name());
  }
}
