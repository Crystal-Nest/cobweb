package it.crystalnest.cobweb.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelHandler;
import it.crystalnest.cobweb.api.pack.delivery.AssetDelivery;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerConnectionListener;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.net.InetAddress;

/**
 * Injects into {@link ServerConnectionListener} to attach asset delivery to remote TCP channels.
 */
@Mixin(ServerConnectionListener.class)
public abstract class ServerConnectionListenerMixin {
  /**
   * Server that owns the native TCP listener.
   */
  @Shadow
  @Final
  private MinecraftServer server;

  /**
   * Wraps the call to {@link ServerBootstrap#childHandler(ChannelHandler)} inside the method {@link ServerConnectionListener#startTcpServerListener(InetAddress, int)}.<br>
   * Gives each TCP channel access to the publication service for its server.
   *
   * @param instance native TCP bootstrap.
   * @param initializer native child channel initializer.
   * @param original native bootstrap operation.
   * @return configured bootstrap.
   */
  @WrapOperation(method = "startTcpServerListener", at = @At(value = "INVOKE", target = "Lio/netty/bootstrap/ServerBootstrap;childHandler(Lio/netty/channel/ChannelHandler;)Lio/netty/bootstrap/ServerBootstrap;"))
  private ServerBootstrap wrapChildHandler(ServerBootstrap instance, ChannelHandler initializer, Operation<ServerBootstrap> original) {
    AssetDelivery.attach(instance, server);
    return original.call(instance, initializer);
  }
}
