package it.crystalnest.cobweb.api.pack.delivery;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import it.crystalnest.cobweb.Constants;
import it.crystalnest.cobweb.api.pack.generation.AssetPackArchives;
import it.crystalnest.cobweb.platform.Services;
import it.crystalnest.cobweb.platform.services.PackHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Owns one dedicated server's publication state and HTTP listeners.
 */
public final class AssetDelivery implements AutoCloseable {
  /**
   * Active publication services, keyed by their owning servers.
   */
  private static final Map<MinecraftServer, AssetDelivery> SERVERS = new ConcurrentHashMap<>();

  /**
   * Time retired URLs remain available to clients that already received them.
   */
  private static final long GRACE_MILLIS = Duration.ofMinutes(15).toMillis();

  /**
   * File names eligible for pruning in the publication directory.
   */
  private static final Pattern ARCHIVE_FILE = Pattern.compile("[a-f0-9]{64}[.]zip");

  /**
   * Temporary ZIP names eligible for pruning in the staging directory.
   */
  private static final Pattern STAGING_FILE = Pattern.compile("[.]pending-\\d+[.]zip");

  /**
   * Address advertised by the client during the Minecraft handshake.
   */
  private static final AttributeKey<InetSocketAddress> INTENTION = AttributeKey.valueOf("cobweb:asset_intention");

  /**
   * Publication service passed to child channels of the shared Minecraft listener.
   */
  private static final AttributeKey<AssetDelivery> DELIVERY = AttributeKey.valueOf("cobweb:asset_delivery");

  /**
   * Dedicated server whose applied data feeds publication callbacks.
   */
  private final DedicatedServer server;

  /**
   * Operator's selection and hosting settings for this server.
   */
  private final AssetDeliveryConfig config;

  /**
   * Asset pack registrations in the operator's chosen priority order.
   */
  private final List<PackHelper.RegisteredPack> packs;

  /**
   * World-local directory of complete, content-addressed archives.
   */
  private final Path directory;

  /**
   * Sibling directory for temporary archives before publication.
   */
  private final Path staging;

  /**
   * Owned worker that writes archives and prunes older files.
   */
  private final ScheduledThreadPoolExecutor worker;

  /**
   * Owned transport for built-in hosting.
   */
  private final AssetPackHttp http;

  /**
   * Index of reusable archives across publication revisions.
   */
  private final AssetPackArchives.Reuse archiveReuse = new AssetPackArchives.Reuse();

  /**
   * Published and retired archive entries indexed by address and guarded by this service's monitor.
   */
  private final Map<String, Served> served = new HashMap<>();

  /**
   * Last complete publication in increasing priority, or {@code null} until the first successful build.
   */
  @Nullable
  private volatile Publication publication;

  /**
   * Failure of the current revision, or {@code null} while building or after success.
   */
  @Nullable
  private volatile Throwable failure;

  /**
   * Most recently scheduled build, or {@code null} before one has been scheduled.
   */
  @Nullable
  private Future<?> pending;

  /**
   * Latest requested publication number, guarded by this service's monitor.
   */
  private long revision;

  /**
   * Whether shutdown has started, guarded by this service's monitor.
   */
  private boolean closed;

  /**
   * Whether the first successful capture has already had a chance to report startup advisories.
   */
  private boolean warned;

  /**
   * @param server dedicated server that owns this publisher.
   * @param config operator's asset delivery configuration.
   * @param packs asset pack registrations in increasing priority.
   */
  private AssetDelivery(DedicatedServer server, AssetDeliveryConfig config, List<PackHelper.RegisteredPack> packs) {
    this.server = server;
    this.config = config;
    this.packs = packs;
    directory = server.getWorldPath(LevelResource.ROOT).resolve("cobweb/published-assets").toAbsolutePath().normalize();
    staging = directory.resolveSibling("published-assets-staging");
    worker = new ScheduledThreadPoolExecutor(1, task -> {
      var thread = new Thread(task, "Cobweb asset publication");
      thread.setDaemon(true);
      return thread;
    });
    worker.setRemoveOnCancelPolicy(true);
    http = new AssetPackHttp(this);
    worker.scheduleWithFixedDelay(this::prune, 1, 1, TimeUnit.MINUTES);
  }

  /**
   * Opens publication for a dedicated server that has packs configured.
   *
   * @param server initializing dedicated server.
   * @throws IOException if the operator configuration cannot be read or parsed.
   * @throws IllegalArgumentException if the operator selects an unregistered asset pack.
   */
  public static void configure(DedicatedServer server) throws IOException {
    var config = AssetDeliveryConfig.read();
    if (!config.packs().isEmpty()) {
      var packs = config.packs().stream().map(id -> {
        var registration = Services.PACK.assetPack(id);
        if (registration == null) {
          throw new IllegalArgumentException("Unknown asset pack " + id + " in " + AssetDeliveryConfig.FILE);
        }
        return registration;
      }).toList();
      SERVERS.put(server, new AssetDelivery(server, config, packs));
    }
  }

  /**
   * Finds the publication service for a server.
   *
   * @param server owning server.
   * @return enabled service, or {@code null} if no packs are configured.
   */
  @Nullable
  public static AssetDelivery get(MinecraftServer server) {
    return SERVERS.get(server);
  }

  /**
   * Starts the configured transport and captures the initial publication after world startup.
   *
   * @param server started dedicated server.
   */
  public static void start(DedicatedServer server) {
    var service = get(server);
    if (service != null) {
      if (service.config.hosting() == AssetDeliveryConfig.Hosting.SEPARATE_PORT) {
        String bind = service.config.bindAddress();
        service.http.listen(bind == null ? server.getLocalIp() : bind, service.config.port());
      }
      Constants.LOGGER.debug("Asset publication service started; transport={}, configured packs (lowest priority first): {}", service.config.hosting(), service.config.packs());
      service.refresh();
    }
  }

  /**
   * Captures another publication after a successful server data reload.
   *
   * @param server server with newly applied resources.
   */
  public static void reload(MinecraftServer server) {
    var service = get(server);
    if (service != null) {
      server.execute(service::refresh);
    }
  }

  /**
   * Closes the server's publication and transport.
   *
   * @param server stopping server.
   */
  public static void stop(MinecraftServer server) {
    var service = SERVERS.remove(server);
    if (service != null) {
      service.close();
    }
  }

  /**
   * Records the host and port requested in a TCP login handshake.
   *
   * @param channel client connection.
   * @param host advertised Minecraft hostname.
   * @param port advertised Minecraft port.
   */
  public static void rememberAddress(Channel channel, String host, int port) {
    channel.attr(INTENTION).set(InetSocketAddress.createUnresolved(host, port));
  }

  /**
   * Makes the shared-port delivery service available to the remote TCP channel initializer.
   *
   * @param bootstrap Minecraft's TCP bootstrap.
   * @param server server that owns the listener.
   */
  public static void attach(ServerBootstrap bootstrap, MinecraftServer server) {
    var delivery = get(server);
    if (delivery != null && delivery.config.hosting() == AssetDeliveryConfig.Hosting.GAME_PORT) {
      bootstrap.childAttr(DELIVERY, delivery);
    }
  }

  /**
   * Installs HTTP detection ahead of the Minecraft packet decoders in shared-port mode.
   *
   * @param channel new remote TCP channel.
   */
  public static void detectHttp(Channel channel) {
    var delivery = channel.attr(DELIVERY).get();
    if (delivery != null) {
      delivery.http.detect(channel);
    }
  }

  /**
   * Resolves the public download base for a player's connection.
   * The local address is the fallback when no hostname was recorded, and the bound port replaces a missing or zero handshake port.
   *
   * @param channel player's TCP channel.
   * @return base URL ending with a slash.
   * @throws IllegalArgumentException if the selected host or port cannot be used.
   */
  URI baseUrl(Channel channel) {
    var address = channel.attr(INTENTION).get();
    var local = (InetSocketAddress) channel.localAddress();
    String host = address == null ? local.getHostString() : address.getHostString();
    int port = address == null || address.getPort() == 0 ? local.getPort() : address.getPort();
    return config.baseUrl(host, local.getPort(), port);
  }

  /**
   * Returns the last complete publication, including while a replacement builds or after a replacement fails.
   *
   * @return complete revision and its ordered archives, or {@code null} before the first success.
   */
  @Nullable
  Publication publication() {
    return publication;
  }

  /**
   * Checks whether the latest publication attempt failed.
   *
   * @return whether the current revision has failed, even if a prior publication is available.
   */
  boolean unavailable() {
    return failure != null;
  }

  /**
   * Captures publication inputs on the server thread and queues archive construction.
   * A newer revision cancels queued work and makes any older running work ineligible for publication.
   */
  private void refresh() {
    long requested;
    synchronized (this) {
      if (closed) {
        return;
      }
      requested = ++revision;
      if (pending != null) {
        pending.cancel(false);
      }
      failure = null;
    }
    Constants.LOGGER.debug("Asset publication revision #{} requested", requested);
    try {
      var build = AssetPackArchives.capture(server, packs, !warned, archiveReuse, () -> current(requested));
      warned = true;
      synchronized (this) {
        if (!closed && revision == requested) {
          pending = worker.submit(() -> build(requested, build));
        }
      }
    } catch (RuntimeException problem) {
      report(requested, problem);
    }
  }

  /**
   * Checks whether work is still eligible for publication.
   *
   * @param requested revision being captured or built.
   * @return whether this revision is current and the service is open.
   */
  private synchronized boolean current(long requested) {
    return !closed && revision == requested;
  }

  /**
   * Writes the captured archives and submits the complete set for publication.
   * Reports failures only if this revision is still current.
   *
   * @param requested revision being built.
   * @param build captured source packs and generated contributions.
   */
  private void build(long requested, AssetPackArchives.Build build) {
    try {
      var archives = build.write(directory, staging);
      publish(requested, archives);
    } catch (IOException | RuntimeException problem) {
      report(requested, problem);
    }
  }

  /**
   * Makes a complete current revision available and starts the grace period for superseded addresses.
   * Addresses retained by the new publication remain available indefinitely while current.
   *
   * @param requested revision that completed.
   * @param archives complete archives in increasing priority.
   */
  private synchronized void publish(long requested, List<AssetPackArchives.Archive> archives) {
    if (closed || revision != requested) {
      return;
    }
    long retireAt = System.currentTimeMillis() + GRACE_MILLIS;
    served.values().forEach(entry -> {
      if (entry.expiresAt == Long.MAX_VALUE) {
        entry.expiresAt = retireAt;
      }
    });
    for (var archive : archives) {
      served.computeIfAbsent(archive.address(), address -> new Served(archive)).expiresAt = Long.MAX_VALUE;
    }
    publication = new Publication(requested, archives);
    Constants.LOGGER.info("Published {} server asset pack(s) in revision #{}", archives.size(), requested);
  }

  /**
   * Records and logs a failure for the current revision, leaving the last complete publication available.
   *
   * @param requested revision that failed.
   * @param problem cause of the publication failure.
   */
  private synchronized void report(long requested, Throwable problem) {
    if (!closed && revision == requested) {
      failure = problem;
      Constants.LOGGER.error("Cannot publish Cobweb asset packs in revision #{}; retained revision: {}", requested, publication == null ? "none" : publication.revision(), problem);
    }
  }

  /**
   * Acquires a reader lease for an available archive.
   * The lease protects the archive from pruning through expiry and must be closed once.
   *
   * @param address lowercase SHA-256 address of the archive.
   * @return reader lease, or {@code null} if the archive is absent, expired, or the service has closed.
   */
  @Nullable
  synchronized Lease acquire(String address) {
    var entry = served.get(address);
    if (closed || entry == null || entry.expiresAt <= System.currentTimeMillis()) {
      return null;
    }
    entry.readers++;
    return new Lease(entry.archive, () -> release(entry));
  }

  /**
   * Releases one reader's reference to an archive.
   *
   * @param entry referenced archive entry.
   */
  private synchronized void release(Served entry) {
    entry.readers--;
  }

  /**
   * Drops expired entries without active readers and prunes old archive and staging files.
   */
  private void prune() {
    long now = System.currentTimeMillis();
    synchronized (this) {
      served.values().removeIf(entry -> entry.expiresAt <= now && entry.readers == 0);
    }
    prune(directory, ARCHIVE_FILE, now);
    prune(staging, STAGING_FILE, now);
  }

  /**
   * Deletes old files with managed names whose addresses are absent from the serving index.
   *
   * @param folder publication or staging directory.
   * @param filenames pattern matching files managed in the directory.
   * @param now current wall-clock time in milliseconds.
   */
  private void prune(Path folder, Pattern filenames, long now) {
    if (!Files.isDirectory(folder)) {
      return;
    }
    try (var files = Files.list(folder)) {
      for (var path : files.filter(candidate -> filenames.matcher(candidate.getFileName().toString()).matches()).toList()) {
        String name = path.getFileName().toString();
        String address = name.substring(0, name.length() - 4);
        boolean eligible;
        synchronized (this) {
          eligible = !closed && !served.containsKey(address);
        }
        if (eligible && Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toMillis() <= now - GRACE_MILLIS) {
          Files.deleteIfExists(path);
        }
      }
    } catch (IOException | RuntimeException problem) {
      Constants.LOGGER.error("Unable to prune archived Cobweb assets under {}", folder, problem);
    }
  }

  /**
   * Stops archive work and HTTP serving, then clears the serving index.
   * Subsequent calls have no effect.
   */
  @Override
  public synchronized void close() {
    if (!closed) {
      closed = true;
      worker.shutdownNow();
      http.close();
      served.clear();
      Constants.LOGGER.debug("Asset publication service stopped");
    }
  }

  /**
   * One complete revision of the archives offered to new connections.
   *
   * @param revision process-local publication revision.
   * @param archives complete archives in increasing priority.
   */
  record Publication(long revision, List<AssetPackArchives.Archive> archives) {}

  /**
   * Reader lease that prevents removal of a published archive while a consumer is using it.
   *
   * @param archive leased archive.
   * @param release action that releases this reader's reference.
   */
  record Lease(AssetPackArchives.Archive archive, Runnable release) implements AutoCloseable {
    @Override
    public void close() {
      release.run();
    }
  }

  /**
   * Availability and reader count for an archive in the serving index.
   */
  private static final class Served {
    /**
     * Complete archive available at this address.
     */
    private final AssetPackArchives.Archive archive;

    /**
     * Wall-clock expiry in milliseconds, or {@link Long#MAX_VALUE} while current.
     */
    private long expiresAt;

    /**
     * Number of active reader leases.
     */
    private int readers;

    /**
     * @param archive complete archive to serve.
     */
    private Served(AssetPackArchives.Archive archive) {
      this.archive = archive;
    }
  }
}
