package it.crystalnest.cobweb.mixin;

import it.crystalnest.cobweb.api.pack.generation.DynamicPackLoads;
import it.crystalnest.cobweb.platform.Services;
import net.minecraft.server.Main;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects into {@link Main} to end registry declarations after Fabric server initialization.
 */
@Mixin(Main.class)
public abstract class MainMixin {
  /**
   * Injects before the call to {@link Util#startTimerHackThread()} inside the method {@link Main#main(String[])}.<br>
   * Closes registry declarations after Fabric invokes the server mod entrypoints.
   *
   * @param ci callback information.
   */
  @Inject(method = "main", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;startTimerHackThread()V"))
  private static void onModInitialization(CallbackInfo ci) {
    Services.REGISTRY.closeDataPackRegistries();
    DynamicPackLoads.closeDeclarations();
  }
}
