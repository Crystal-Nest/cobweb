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
import org.jetbrains.annotations.ApiStatus;
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
@ApiStatus.Internal
public final class AssetDelivery implements AutoCloseable {
  private static final Map<MinecraftServer, AssetDelivery> SERVERS = new ConcurrentHashMap<>();

  private static final long GRACE_MILLIS = Duration.ofMinutes(15).toMillis();

  private static final Pattern ARCHIVE_FILE = Pattern.compile("[a-f0-9]{64}[.]zip");

  private static final Pattern STAGING_FILE = Pattern.compile("[.]pending-[0-9]+[.]zip");

  private static final AttributeKey<InetSocketAddress> INTENTION = AttributeKey.valueOf("cobweb:asset_intention");

  private static final AttributeKey<AssetDelivery> DELIVERY = AttributeKey.valueOf("cobweb:asset_delivery");

  private final DedicatedServer server;

  private final AssetDeliveryConfig config;

  /**
   * Asset pack registrations in the operator's chosen priority order.
   */
  private final List<PackHelper.RegisteredPack> packs;

  private final Path directory;

  private final Path staging;

  private final ScheduledThreadPoolExecutor worker;

  private final AssetPackHttp http;

  private final AssetPackArchives.Reuse archiveReuse = new AssetPackArchives.Reuse();

  private final Map<String, Served> served = new HashMap<>();

  @Nullable
  private volatile List<AssetPackArchives.Archive> publication;

  @Nullable
  private volatile Throwable failure;

  @Nullable
  private Future<?> pending;

  private long revision;

  private boolean closed;

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
   * @throws IOException if the operator configuration cannot be used.
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

  URI baseUrl(Channel channel) {
    var address = channel.attr(INTENTION).get();
    var local = (InetSocketAddress) channel.localAddress();
    String host = address == null ? local.getHostString() : address.getHostString();
    int port = address == null || address.getPort() == 0 ? local.getPort() : address.getPort();
    return config.baseUrl(host, local.getPort(), port);
  }

  @Nullable
  List<AssetPackArchives.Archive> publication() {
    return publication;
  }

  boolean unavailable() {
    return failure != null;
  }

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

  private synchronized boolean current(long requested) {
    return !closed && revision == requested;
  }

  private void build(long requested, AssetPackArchives.Build build) {
    try {
      var archives = build.write(directory, staging);
      publish(requested, archives);
    } catch (IOException | RuntimeException problem) {
      report(requested, problem);
    }
  }

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
    publication = archives;
    Constants.LOGGER.info("Published {} server asset pack(s)", archives.size());
  }

  private synchronized void report(long requested, Throwable problem) {
    if (!closed && revision == requested) {
      failure = problem;
      Constants.LOGGER.error("Cannot publish Cobweb asset packs; keeping the last complete publication if available", problem);
    }
  }

  @Nullable
  synchronized Lease acquire(String address) {
    var entry = served.get(address);
    if (closed || entry == null || entry.expiresAt <= System.currentTimeMillis()) {
      return null;
    }
    entry.readers++;
    return new Lease(entry.archive, () -> release(entry));
  }

  private synchronized void release(Served entry) {
    entry.readers--;
  }

  private void prune() {
    long now = System.currentTimeMillis();
    synchronized (this) {
      served.values().removeIf(entry -> entry.expiresAt <= now && entry.readers == 0);
    }
    prune(directory, ARCHIVE_FILE, now);
    prune(staging, STAGING_FILE, now);
  }

  private void prune(Path folder, Pattern filenames, long now) {
    if (!Files.isDirectory(folder)) {
      return;
    }
    try (var files = Files.list(folder)) {
      for (var path : files.filter(candidate -> filenames.matcher(candidate.getFileName().toString()).matches()).toList()) {
        String name = path.getFileName().toString();
        String address = name.substring(0, name.length() - 4);
        synchronized (this) {
          if (closed || served.containsKey(address)) {
            continue;
          }
        }
        if (Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toMillis() <= now - GRACE_MILLIS) {
          Files.deleteIfExists(path);
        }
      }
    } catch (IOException | RuntimeException problem) {
      Constants.LOGGER.warn("Unable to prune archived Cobweb assets under {}", folder, problem);
    }
  }

  @Override
  public synchronized void close() {
    if (!closed) {
      closed = true;
      worker.shutdownNow();
      http.close();
      served.clear();
    }
  }

  record Lease(AssetPackArchives.Archive archive, Runnable release) implements AutoCloseable {
    @Override
    public void close() {
      release.run();
    }
  }

  private static final class Served {
    private final AssetPackArchives.Archive archive;

    private long expiresAt;

    private int readers;

    private Served(AssetPackArchives.Archive archive) {
      this.archive = archive;
    }
  }
}
