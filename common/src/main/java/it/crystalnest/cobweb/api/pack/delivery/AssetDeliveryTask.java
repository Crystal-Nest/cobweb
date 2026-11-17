package it.crystalnest.cobweb.api.pack.delivery;

import io.netty.channel.Channel;
import it.crystalnest.cobweb.Constants;
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
import org.jspecify.annotations.NonNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Offers one publication revision and retries an ordered batch once after a download failure or discard.
 */
public final class AssetDeliveryTask implements ConfigurationTask {
  /**
   * Native configuration task type for Cobweb delivery.
   */
  private static final Type TYPE = new Type("cobweb:asset_packs");

  /**
   * Maximum wait for the first complete publication.
   */
  private static final long PREPARATION_TIMEOUT = Duration.ofSeconds(30).toMillis();

  /**
   * Time allowed for the whole offer, including any retry.
   */
  private static final long APPLICATION_TIMEOUT = Duration.ofMinutes(2).toMillis();

  /**
   * Assigns transient IDs to connection offers without identifying players.
   */
  private static final AtomicLong nextOfferId = new AtomicLong();

  /**
   * Identifies this offer and any retry in the server log.
   */
  private final long offerId = nextOfferId.incrementAndGet();

  /**
   * Published revision pinned to this connection, or zero before an archive is offered.
   */
  private long revision;

  /**
   * Server publication service used by this connection.
   */
  private final AssetDelivery delivery;

  /**
   * Player's TCP channel, used to resolve the advertised address.
   */
  private final Channel channel;

  /**
   * Whether the server requires clients to accept and apply the offered packs.
   */
  private final boolean required;

  /**
   * Optional consent prompt attached to published packs.
   */
  private final Optional<Component> prompt;

  /**
   * Optional native offer configured in {@code server.properties}.
   */
  private final Optional<MinecraftServer.ServerResourcePackInfo> configured;

  /**
   * Action that disconnects this client with the supplied reason.
   */
  private final Consumer<Component> disconnect;

  /**
   * All offer IDs used by this task, including retired IDs whose replies may still arrive.
   */
  private final Set<UUID> sent = new HashSet<>();

  /**
   * Offers still awaiting terminal replies, keyed by UUID with labels for diagnostics.
   */
  private final Map<UUID, String> pending = new LinkedHashMap<>();

  /**
   * Most recently sent batch in increasing priority.
   */
  private List<Offer> batch = List.of();

  /**
   * Recorded download failure or discard awaiting a batch retry, or {@code null} when none is pending.
   */
  @Nullable
  private String retryableFailure;

  /**
   * Whether this task can still retry the batch after a download failure or discard.
   */
  private boolean retryAllowed = true;

  /**
   * Packet sink supplied when native configuration starts the task.
   */
  private Consumer<Packet<?>> send;

  /**
   * Monotonic deadline for the current preparation or application window.
   */
  private long deadline;

  /**
   * Current state of this connection's pack configuration task.
   */
  private Phase phase = Phase.PREPARING;

  /**
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
  public @NonNull Type type() {
    return TYPE;
  }

  /**
   * Stores the native packet sink and starts the publication wait.
   *
   * @param send packet sink for the player's connection.
   */
  @Override
  public void start(@NonNull Consumer<Packet<?>> send) {
    this.send = send;
    deadline = Util.getMillis() + PREPARATION_TIMEOUT;
    Constants.LOGGER.debug("Asset offer #{} waiting for publication; required={}", offerId, required);
  }

  /**
   * Waits for publication, advances the current offer, or performs the single retry within the application deadline.
   * An optional timeout removes the pending offers and allows configuration to continue. A required timeout rejects the connection.
   *
   * @return whether configuration may proceed to the next task.
   */
  @Override
  public boolean tick() {
    if (phase == Phase.REJECTED || phase == Phase.COMPLETE) {
      return phase == Phase.COMPLETE;
    }
    if (phase == Phase.PREPARING) {
      var publication = delivery.publication();
      if (publication == null && !delivery.unavailable() && Util.getMillis() < deadline) {
        return false;
      }
      try {
        if (publication != null) {
          offer(publication);
        } else {
          publicationUnavailable(delivery.unavailable() ? "Cobweb asset publication failed" : "timed out waiting for Cobweb asset publication");
        }
      } catch (IllegalArgumentException failure) {
        publicationUnavailable(failure.getMessage());
      }
    }
    if (!pending.isEmpty() && Util.getMillis() >= deadline) {
      if (required) {
        Constants.LOGGER.error("{} timed out applying required server resource packs {}; disconnecting", label(), pending.values());
        reject(Component.literal("Timed out applying required server resource packs: " + String.join(", ", pending.values())));
        return false;
      }
      Constants.LOGGER.error("{}: Timed out applying optional server resource packs {}; allowing join to continue", label(), pending.values());
      pending.keySet().forEach(id -> send.accept(new ClientboundResourcePackPopPacket(Optional.of(id))));
      pending.clear();
    } else if (pending.isEmpty() && retryableFailure != null) {
      if (Util.getMillis() < deadline) {
        retry();
      } else if (required) {
        Constants.LOGGER.error("{} failed with {} at the application deadline; disconnecting", label(), retryableFailure);
        reject(packFailure(retryableFailure));
      } else {
        Constants.LOGGER.error("{} failed with {} at the application deadline; allowing join to continue", label(), retryableFailure);
      }
    }
    if (phase != Phase.REJECTED && pending.isEmpty()) {
      phase = Phase.COMPLETE;
      Constants.LOGGER.debug("{} completed; allowing join to continue", label());
    }
    return phase == Phase.COMPLETE;
  }

  /**
   * Handles a native result for a UUID offered on this connection.
   * Terminal replies remove pending entries. Replies for retired attempts are consumed without altering the current batch.
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
      if (label != null) {
        Constants.LOGGER.debug("{} received {} for {}", label(), packet.action(), label);
        if (packet.action() != Action.SUCCESSFULLY_LOADED) {
          handleFailure(label, packet.action());
        }
      }
    }
    return true;
  }

  /**
   * Records a retryable failure or applies the required or optional failure policy.
   *
   * @param label pack label for diagnostics.
   * @param action unsuccessful terminal status.
   */
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
      if (action == Action.DECLINED) {
        Constants.LOGGER.info("{}: required server resource pack {} declined; disconnecting", label(), label);
      } else {
        Constants.LOGGER.error("{}: Required server resource pack {} failed with {}; disconnecting", label(), label, action);
      }
      reject(action == Action.DECLINED ? Component.translatable("multiplayer.requiredTexturePrompt.disconnect") : packFailure(detail));
    } else if (action != Action.DECLINED) {
      Constants.LOGGER.error("{}: Optional server resource pack {} failed with {}; allowing join to continue", label(), label, action);
    }
  }

  /**
   * Creates the disconnect message for a failed required pack.
   *
   * @param detail diagnostic identifying the pack and its unsuccessful status.
   * @return disconnect reason.
   */
  private static Component packFailure(String detail) {
    return Component.literal("Failed to apply required server resource pack " + detail);
  }

  /**
   * Offers the native pack first, followed by the published archives in increasing priority.
   *
   * @param publication last complete publication.
   * @throws IllegalArgumentException if the base URL cannot be resolved or a published archive duplicates the native offer ID.
   */
  private void offer(AssetDelivery.Publication publication) {
    revision = publication.revision();
    var base = delivery.baseUrl(channel);
    var offers = new ArrayList<Offer>();
    configured.ifPresent(info -> offers.add(configuredOffer(info)));
    for (var archive : publication.archives()) {
      if (configured.isPresent() && configured.get().id().equals(archive.uuid())) {
        throw new IllegalArgumentException("Duplicate native offer UUID for " + archive.id());
      }
      String url = base.resolve(archive.address() + ".zip").toASCIIString();
      offers.add(new Offer(archive.uuid(), url, archive.sha1(), required, prompt, archive.id().toString()));
    }
    startOffers(offers);
  }

  /**
   * Rejects a required connection when publication is unavailable, or continues an optional connection with only the native offer if configured.
   *
   * @param reason publication or offer preparation failure.
   */
  private void publicationUnavailable(String reason) {
    phase = Phase.APPLYING;
    if (required) {
      Constants.LOGGER.error("{}: {}; disconnecting", label(), reason);
      reject(Component.literal("The server's required resource packs are unavailable. Try again later or contact the server operator."));
    } else {
      Constants.LOGGER.error("{}: {}; continuing with {}", label(), reason, configured.isPresent() ? "only the server.properties pack" : "no resource packs");
      configured.ifPresent(info -> startOffers(List.of(configuredOffer(info))));
    }
  }

  /**
   * Converts the native configuration to a batch offer under the server-wide requirement policy.
   *
   * @param info native pack settings.
   * @return offer for the pack from {@code server.properties}.
   */
  private Offer configuredOffer(MinecraftServer.ServerResourcePackInfo info) {
    return new Offer(info.id(), info.url(), info.hash(), required, Optional.ofNullable(info.prompt()), "server.properties");
  }

  /**
   * Enters the application phase, sets its deadline, and sends the first batch.
   *
   * @param offers offers in increasing priority.
   */
  private void startOffers(List<Offer> offers) {
    phase = Phase.APPLYING;
    deadline = Util.getMillis() + APPLICATION_TIMEOUT;
    offers.forEach(offer -> sent.add(offer.id()));
    sendBatch(offers);
  }

  /**
   * Removes the prior offers and resends the batch under fresh UUIDs in the same order.
   * The original application deadline still applies.
   */
  private void retry() {
    Constants.LOGGER.info("{}: Retrying server resource pack batch after {}", label(), retryableFailure);
    retryAllowed = false;
    retryableFailure = null;
    var offers = new ArrayList<Offer>();
    batch.forEach(offer -> {
      send.accept(new ClientboundResourcePackPopPacket(Optional.of(offer.id())));
      UUID id;
      do {
        id = UUID.randomUUID();
      } while (!sent.add(id));
      offers.add(new Offer(id, offer.url(), offer.hash(), offer.required(), offer.prompt(), offer.label()));
    });
    sendBatch(offers);
  }

  /**
   * Records a batch as pending and sends its native push packets in order.
   *
   * @param offers offers in increasing priority.
   */
  private void sendBatch(List<Offer> offers) {
    batch = List.copyOf(offers);
    if (Constants.LOGGER.isDebugEnabled()) {
      Constants.LOGGER.debug("{} sending packs (lowest priority first): {}", label(), offers.stream().map(Offer::label).toList());
    }
    offers.forEach(offer -> pending.put(offer.id(), offer.label()));
    offers.forEach(offer -> send.accept(new ClientboundResourcePackPushPacket(offer.id(), offer.url(), offer.hash(), offer.required(), offer.prompt())));
  }

  /**
   * Identifies the offer and its pinned publication in diagnostic messages.
   *
   * @return process-local offer label.
   */
  private String label() {
    return "Asset offer #" + offerId + (revision == 0 ? "" : " (revision #" + revision + ")");
  }

  /**
   * Marks configuration rejected and closes the connection.
   *
   * @param message disconnect reason shown to the client.
   */
  private void reject(Component message) {
    phase = Phase.REJECTED;
    disconnect.accept(message);
  }

  /**
   * Progress states for the native configuration task.
   */
  private enum Phase {
    /**
     * Waiting for the first publication or its preparation deadline.
     */
    PREPARING,
    /**
     * Processing the offer batch or its single retry.
     */
    APPLYING,
    /**
     * Allowing native configuration to continue.
     */
    COMPLETE,
    /**
     * Terminating configuration with a disconnect reason.
     */
    REJECTED
  }

  /**
   * One native resource-pack offer in the batch.
   *
   * @param id UUID used by the current attempt.
   * @param url public download URL.
   * @param hash SHA-1 hash supplied to the native client.
   * @param required whether applying this pack is mandatory.
   * @param prompt optional native consent prompt.
   * @param label pack name used in diagnostics.
   */
  private record Offer(UUID id, String url, String hash, boolean required, Optional<Component> prompt, String label) {}
}
