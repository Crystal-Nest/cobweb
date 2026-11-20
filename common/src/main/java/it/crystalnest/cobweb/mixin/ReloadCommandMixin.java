package it.crystalnest.cobweb.mixin;

import it.crystalnest.cobweb.api.pack.selection.DataPackSelection;
import net.minecraft.server.commands.ReloadCommand;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.storage.WorldData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

/**
 * Injects into {@link ReloadCommand} to preserve static pack activation and initial placement during discovery.
 */
@Mixin(ReloadCommand.class)
public abstract class ReloadCommandMixin {
  /**
   * Injects at the return of the method {@link ReloadCommand#discoverNewPacks(PackRepository, WorldData, Collection)}.<br>
   * Applies activation and initial placement to newly discovered Cobweb data packs.
   *
   * @param packRepository reloaded native repository.
   * @param worldData current world data.
   * @param currentPacks selection before discovery.
   * @param cir callback containing the native discovered selection.
   */
  @Inject(method = "discoverNewPacks", at = @At("RETURN"), cancellable = true)
  private static void onDiscoverNewPacks(PackRepository packRepository, WorldData worldData, Collection<String> currentPacks, CallbackInfoReturnable<Collection<String>> cir) {
    cir.setReturnValue(DataPackSelection.discover(packRepository, worldData, currentPacks, cir.getReturnValue()));
  }
}
