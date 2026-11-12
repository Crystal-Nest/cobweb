package it.crystalnest.cobweb.mixin;

import io.netty.channel.Channel;
import it.crystalnest.cobweb.impl.pack.delivery.AssetDelivery;
import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerConnectionListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects into {@link ServerConnectionListener}'s TCP channel initializer to route shared-port asset downloads.
 */
@Mixin(targets = "net.minecraft.server.network.ServerConnectionListener$1")
public abstract class ServerConnectionListenerChannelInitializerMixin {
  /**
   * Injects before {@link Connection#configureSerialization} in the native TCP channel initializer.<br>
   * Lets the asset HTTP handler identify download requests ahead of the Minecraft packet codecs.
   *
   * @param channel newly opened TCP channel.
   * @param ci callback information.
   */
  @Inject(method = "initChannel(Lio/netty/channel/Channel;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;configureSerialization(Lio/netty/channel/ChannelPipeline;Lnet/minecraft/network/protocol/PacketFlow;ZLnet/minecraft/network/BandwidthDebugMonitor;)V"))
  private void onConfigureSerialization(Channel channel, CallbackInfo ci) {
    AssetDelivery.detectHttp(channel);
  }
}
