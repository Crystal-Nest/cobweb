package it.crystalnest.cobweb.mixin;

import it.crystalnest.cobweb.impl.pack.delivery.AssetDelivery;
import net.minecraft.server.dedicated.DedicatedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;

/**
 * Injects into {@link DedicatedServer} to enable configured asset publication.
 */
@Mixin(DedicatedServer.class)
public abstract class DedicatedServerMixin {
  /**
   * Injects at the start of the method {@link DedicatedServer#initServer()}.<br>
   * Reads delivery settings before Minecraft binds its listener.
   *
   * @param cir callback information.
   * @throws IOException if the configuration cannot be read.
   */
  @Inject(method = "initServer", at = @At("HEAD"))
  private void onInitServer(CallbackInfoReturnable<Boolean> cir) throws IOException {
    AssetDelivery.configure((DedicatedServer) (Object) this);
  }
}
