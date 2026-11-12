package it.crystalnest.cobweb.mixin;

import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Injects into {@link Connection} to access the channel used by a remote pack client.
 */
@Mixin(Connection.class)
public interface ConnectionAccessor {
  /**
   * Returns the connected client's Netty channel.
   *
   * @return connection channel.
   */
  @Accessor("channel")
  Channel getDeliveryChannel();
}
