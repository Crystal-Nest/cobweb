package it.crystalnest.cobweb.mixin;

import it.crystalnest.cobweb.api.pack.delivery.AssetDelivery;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerHandshakePacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects into {@link ServerHandshakePacketListenerImpl} to observe the hostname requested by a connecting client.
 */
@Mixin(ServerHandshakePacketListenerImpl.class)
public abstract class ServerHandshakePacketListenerImplMixin {
  /**
   * Server receiving this handshake.
   */
  @Shadow
  @Final
  private MinecraftServer server;

  /**
   * Connection whose requested hostname supplies the default asset download URL.
   */
  @Shadow
  @Final
  private Connection connection;

  /**
   * Injects at the start of the method {@link ServerHandshakePacketListenerImpl#beginLogin(ClientIntentionPacket, boolean)}.<br>
   * Records the requested hostname and port for the default asset download URL.
   *
   * @param packet client login intention.
   * @param transfer whether this follows a transfer.
   * @param ci callback information.
   */
  @Inject(method = "beginLogin", at = @At("HEAD"))
  private void onBeginLogin(ClientIntentionPacket packet, boolean transfer, CallbackInfo ci) {
    if (AssetDelivery.get(server) != null) {
      AssetDelivery.rememberAddress(((ConnectionAccessor) connection).getDeliveryChannel(), packet.hostName(), packet.port());
    }
  }
}
