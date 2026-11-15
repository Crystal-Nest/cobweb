package it.crystalnest.cobweb.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.crystalnest.cobweb.api.pack.delivery.AssetDelivery;
import it.crystalnest.cobweb.api.pack.delivery.AssetDeliveryTask;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.network.config.ServerResourcePackConfigurationTask;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.Queue;

/**
 * Injects into {@link ServerConfigurationPacketListenerImpl} to offer and track the server's ordered asset batch.
 */
@Mixin(ServerConfigurationPacketListenerImpl.class)
public abstract class ServerConfigurationPacketListenerImplMixin extends ServerCommonPacketListenerImpl {
  /**
   * Native queue of tasks required before the player enters the game.
   */
  @Shadow
  @Final
  private Queue<ConfigurationTask> configurationTasks;

  /**
   * Asset delivery task for this connection, or {@code null} when Cobweb delivery is inactive.
   */
  @Unique
  private AssetDeliveryTask assetDeliveryTask;

  /**
   * @param server server configuring the player connection.
   * @param connection native client connection.
   * @param cookie state carried from the preceding connection phase.
   */
  protected ServerConfigurationPacketListenerImplMixin(MinecraftServer server, Connection connection, CommonListenerCookie cookie) {
    super(server, connection, cookie);
  }

  /**
   * Wraps the call to {@link MinecraftServer#getServerResourcePack()} inside {@link ServerConfigurationPacketListenerImpl#addOptionalTasks()}.<br>
   * Queues one asset task including the configured native offer when server delivery is enabled.
   *
   * @param instance server that provides the configured pack.
   * @param original native lookup.
   * @return native offer for Minecraft to queue, or an empty result when the asset task owns it.
   */
  @WrapOperation(method = "addOptionalTasks", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;getServerResourcePack()Ljava/util/Optional;"))
  private Optional<MinecraftServer.ServerResourcePackInfo> wrapGetServerResourcePack(MinecraftServer instance, Operation<Optional<MinecraftServer.ServerResourcePackInfo>> original) {
    var configured = original.call(instance);
    var delivery = AssetDelivery.get(instance);
    if (delivery == null || !(instance instanceof DedicatedServer dedicated)) {
      return configured;
    }
    var properties = ((SettingsAccessor) (Object) dedicated.getProperties()).getProperties();
    boolean required = configured.map(MinecraftServer.ServerResourcePackInfo::isRequired).orElseGet(() -> Boolean.parseBoolean(properties.getProperty("require-resource-pack")));
    var prompt = configured.isPresent() ? configured.get().prompt() : DedicatedServerPropertiesAccessor.invokeParseResourcePackPrompt(properties.getProperty("resource-pack-prompt", ""));
    assetDeliveryTask = new AssetDeliveryTask(delivery, ((ConnectionAccessor) connection).getDeliveryChannel(), required, prompt, configured, this::disconnect);
    configurationTasks.add(assetDeliveryTask);
    return Optional.empty();
  }

  /**
   * Injects at the start of {@link ServerConfigurationPacketListenerImpl#handleResourcePackResponse(ServerboundResourcePackPacket)}.<br>
   * Resolves replies for offers owned by the asset task on the server thread.
   *
   * @param packet native client response.
   * @param ci callback information.
   */
  @Inject(method = "handleResourcePackResponse", at = @At("HEAD"), cancellable = true)
  private void onHandleResourcePackResponse(ServerboundResourcePackPacket packet, CallbackInfo ci) {
    if (assetDeliveryTask != null) {
      PacketUtils.ensureRunningOnSameThread(packet, (ServerConfigurationPacketListenerImpl) (Object) this, server.packetProcessor());
      if (assetDeliveryTask.receive(packet)) {
        ci.cancel();
      }
    }
  }

  /**
   * Guards the call to {@link ServerConfigurationPacketListenerImpl#finishCurrentTask(ConfigurationTask.Type)} inside
   * {@link ServerConfigurationPacketListenerImpl#handleResourcePackResponse(ServerboundResourcePackPacket)}.<br>
   * Prevents stray terminal replies from finishing the native task while its offer belongs to the asset task.
   *
   * @param instance native configuration listener.
   * @param type native task type.
   * @return whether to run native task completion.
   */
  @WrapWithCondition(method = "handleResourcePackResponse", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerConfigurationPacketListenerImpl;finishCurrentTask(Lnet/minecraft/server/network/ConfigurationTask$Type;)V"))
  private boolean allowFinishCurrentTask(ServerConfigurationPacketListenerImpl instance, ConfigurationTask.Type type) {
    return assetDeliveryTask == null || !ServerResourcePackConfigurationTask.TYPE.equals(type);
  }
}
