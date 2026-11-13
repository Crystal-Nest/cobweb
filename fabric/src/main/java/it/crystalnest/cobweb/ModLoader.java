package it.crystalnest.cobweb;

import it.crystalnest.cobweb.api.pack.query.PackExportCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.jetbrains.annotations.ApiStatus;

/**
 * Mod loader.
 */
@ApiStatus.Internal
public final class ModLoader implements ModInitializer {
  @Override
  public void onInitialize() {
    CommonModLoader.init();
    CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> PackExportCommand.register(dispatcher));
  }
}
