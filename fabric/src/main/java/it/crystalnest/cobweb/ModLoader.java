package it.crystalnest.cobweb;

import it.crystalnest.cobweb.api.pack.command.PackExportCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.ApiStatus;

/**
 * Mod loader.
 */
@ApiStatus.Internal
public final class ModLoader implements ModInitializer {
  @Override
  public void onInitialize() {
    var loader = FabricLoader.getInstance();
    String version = version(loader);
    CommonModLoader.logSetup("common setup started", version, "Fabric", loader.getEnvironmentType().name());
    CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> PackExportCommand.registerServer(dispatcher));
    CommonModLoader.logSetup("registration services ready; common setup completed", version, "Fabric", loader.getEnvironmentType().name());
  }

  /**
   * Returns the Cobweb version as reported by Fabric Loader.
   *
   * @param loader active Fabric Loader.
   * @return version string from the mod metadata.
   */
  static String version(FabricLoader loader) {
    return loader.getModContainer(Constants.MOD_ID).map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
  }
}
