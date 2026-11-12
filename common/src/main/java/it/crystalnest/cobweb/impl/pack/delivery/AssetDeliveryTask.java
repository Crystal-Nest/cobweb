package it.crystalnest.cobweb.impl.pack.delivery;

import io.netty.channel.Channel;
import it.crystalnest.cobweb.Constants;
import it.crystalnest.cobweb.api.pack.AssetPackArchives;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket.Action;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Offers one publication revision and retries an ordered batch once after a download failure or discard.
 */
public final class AssetDeliveryTask implements ConfigurationTask {
  private static final Type TYPE = new Type("cobweb:asset_packs");

  private static final long PREPARATION_TIMEOUT = Duration.ofSeconds(30).toMillis();

  private static final long APPLICATION_TIMEOUT = Duration.ofMinutes(2).toMillis();

  private final AssetDelivery delivery;

  private final Channel channel;

  private final boolean required;

  private final Optional<Component> prompt;

  private final Optional<MinecraftServer.ServerResourcePackInfo> configured;

  private final Consumer<Component> disconnect;

  private final Set<UUID> sent = new HashSet<>();

  private final Map<UUID, String> pending = new LinkedHashMap<>();

  private Map<ClientboundResourcePackPushPacket, String> batch = Map.of();

  @Nullable
  private String retryableFailure;

  private boolean retryAllowed = true;

  private Consumer<Packet<?>> send;

  private long deadline;

  private boolean offered;

  private boolean rejected;

  /**
   * Creates a task for one connection.
   *
   * @param delivery server's publication service.
   * @param channel player's TCP channel.
   * @param required server-wide pack setting.
   * @param prompt server's optional consent prompt.
   * @param configured existing native pack offer, if present.
   * @param disconnect closes the connection with a reason.
   */
  public AssetDeliveryTask(AssetDelivery delivery, Channel channel, boolean required, @Nullable Component prompt, Optional<MinecraftServer.ServerResourcePackInfo> configured,
    Consumer<Component> disconnect) {
    this.delivery = delivery;
    this.channel = channel;
    this.required = required;
    this.prompt = Optional.ofNullable(prompt);
    this.configured = configured;
    this.disconnect = disconnect;
  }

  @Override
  public Type type() {
    return TYPE;
  }

  @Override
  public void start(Consumer<Packet<?>> send) {
    this.send = send;
    deadline = Util.getMillis() + PREPARATION_TIMEOUT;
  }

  @Override
  public boolean tick() {
    if (rejected) {
      return false;
    }
    if (!offered) {
      var publication = delivery.publication();
      if (publication == null && !delivery.unavailable() && Util.getMillis() < deadline) {
        return false;
      }
      try {
        if (publication != null) {
          offer(publication);
        } else {
          publicationUnavailable();
        }
      } catch (IllegalArgumentException failure) {
        Constants.LOGGER.warn("Cannot prepare Cobweb asset pack offers", failure);
        publicationUnavailable();
      }
    }
    if (!pending.isEmpty() && Util.getMillis() >= deadline) {
      if (required) {
        reject(Component.literal("Timed out applying required server resource packs: " + String.join(", ", pending.values())));
        return false;
      }
      Constants.LOGGER.warn("Timed out applying optional server resource packs {}", pending.values());
      pending.keySet().forEach(id -> send.accept(new ClientboundResourcePackPopPacket(Optional.of(id))));
      pending.clear();
    } else if (pending.isEmpty() && retryableFailure != null) {
      if (Util.getMillis() < deadline) {
        retry();
      } else if (required) {
        reject(packFailure(retryableFailure));
      }
    }
    return !rejected && pending.isEmpty();
  }

  /**
   * Handles a native result for a UUID offered on this connection.
   *
   * @param packet client pack result.
   * @return whether the UUID belongs to this task.
   */
  public boolean receive(ServerboundResourcePackPacket packet) {
    if (!sent.contains(packet.id())) {
      return false;
    }
    if (packet.action().isTerminal()) {
      String label = pending.remove(packet.id());
      if (label != null && packet.action() != Action.SUCCESSFULLY_LOADED) {
        handleFailure(label, packet.action());
      }
    }
    return true;
  }

  private void handleFailure(String label, Action action) {
    String detail = label + " (" + action + ")";
    if (retryAllowed && (action == Action.FAILED_DOWNLOAD || action == Action.DISCARDED)) {
      if (retryableFailure == null || action == Action.FAILED_DOWNLOAD) {
        retryableFailure = detail;
      }
      return;
    }
    retryAllowed = false;
    retryableFailure = null;
    if (required) {
      reject(action == Action.DECLINED ? Component.translatable("multiplayer.requiredTexturePrompt.disconnect") : packFailure(detail));
    } else if (action != Action.DECLINED) {
      Constants.LOGGER.warn("Optional server resource pack {} failed with {}", label, action);
    }
  }

  private static Component packFailure(String detail) {
    return Component.literal("Failed to apply required server resource pack " + detail);
  }

  private void offer(List<AssetPackArchives.Archive> archives) {
    var base = delivery.baseUrl(channel);
    var packets = new LinkedHashMap<ClientboundResourcePackPushPacket, String>();
    configured.ifPresent(info -> packets.put(configuredPacket(info), "server.properties"));
    for (var archive : archives) {
      if (configured.isPresent() && configured.get().id().equals(archive.uuid())) {
        throw new IllegalArgumentException("Duplicate native offer UUID for " + archive.id());
      }
      String url = base.resolve(archive.address() + ".zip").toASCIIString();
      packets.put(new ClientboundResourcePackPushPacket(archive.uuid(), url, archive.sha1(), required, prompt), archive.id().toString());
    }
    startOffers(packets);
  }

  private void publicationUnavailable() {
    offered = true;
    if (required) {
      reject(Component.literal("The server's required resource packs are unavailable. Try again later or contact the server operator."));
    } else {
      Constants.LOGGER.debug("No Cobweb asset publication was available for an optional offer");
      configured.ifPresent(info -> startOffers(Map.of(configuredPacket(info), "server.properties")));
    }
  }

  private ClientboundResourcePackPushPacket configuredPacket(MinecraftServer.ServerResourcePackInfo info) {
    return new ClientboundResourcePackPushPacket(info.id(), info.url(), info.hash(), required, Optional.ofNullable(info.prompt()));
  }

  private void startOffers(Map<ClientboundResourcePackPushPacket, String> packets) {
    offered = true;
    deadline = Util.getMillis() + APPLICATION_TIMEOUT;
    packets.keySet().forEach(packet -> sent.add(packet.id()));
    sendBatch(packets);
  }

  private void retry() {
    Constants.LOGGER.info("Retrying server resource pack batch after {}", retryableFailure);
    retryAllowed = false;
    retryableFailure = null;
    var packets = new LinkedHashMap<ClientboundResourcePackPushPacket, String>();
    batch.forEach((packet, label) -> {
      send.accept(new ClientboundResourcePackPopPacket(Optional.of(packet.id())));
      UUID id;
      do {
        id = UUID.randomUUID();
      } while (!sent.add(id));
      packets.put(new ClientboundResourcePackPushPacket(id, packet.url(), packet.hash(), packet.required(), packet.prompt()), label);
    });
    sendBatch(packets);
  }

  private void sendBatch(Map<ClientboundResourcePackPushPacket, String> packets) {
    batch = packets;
    packets.forEach((packet, label) -> pending.put(packet.id(), label));
    packets.keySet().forEach(send);
  }

  private void reject(Component message) {
    rejected = true;
    disconnect.accept(message);
  }
}
