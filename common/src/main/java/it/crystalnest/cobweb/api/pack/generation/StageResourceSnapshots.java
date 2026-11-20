package it.crystalnest.cobweb.api.pack.generation;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestOutputStream;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Shares immutable first-read captures of pack entries across one generation stage.
 * Each pack opening or bundled layer owns a separate set of entries.
 */
final class StageResourceSnapshots implements AutoCloseable {
  /**
   * The native resource type captured by this stage.
   */
  private final PackType type;

  /**
   * First-read captures indexed by the identity of their source pack and file ID.
   */
  private final Map<PackResources, Map<Identifier, Capture>> sources = new IdentityHashMap<>();

  /**
   * Whether the stage has released ownership and refuses new captures.
   */
  private boolean closed;

  /**
   * @param type native resource type to capture.
   */
  StageResourceSnapshots(PackType type) {
    this.type = type;
  }

  /**
   * Wraps a pack opening with first-read captures while retaining native metadata and listing behavior.
   *
   * @param source native pack opening or bundled layer.
   * @return non-owning pack view that captures file reads.
   */
  PackResources wrap(PackResources source) {
    return new Source(source);
  }

  /**
   * Wraps a resource supplier when the resource belongs to this stage's pack type.
   *
   * @param source owning pack opening or bundled layer.
   * @param type native type requested by the caller.
   * @param id requested file identifier.
   * @param original native supplier, or {@code null} when the resource is absent.
   * @return capture-backed supplier, or the original supplier for an absent resource or another type.
   */
  @Nullable
  IoSupplier<InputStream> wrap(PackResources source, PackType type, Identifier id, @Nullable IoSupplier<InputStream> original) {
    return original == null || type != this.type ? original : () -> {
      var stream = new SnapshotStream(capture(source, id), original);
      long limit = PackIO.captureLimit();
      if (limit != Long.MAX_VALUE) {
        stream.initialize(limit);
      }
      return stream;
    };
  }

  /**
   * Returns the shared capture for one resolved resource.
   *
   * @param source owning pack opening or bundled layer.
   * @param id resolved file identifier.
   * @return existing or new capture for this source and ID.
   * @throws IOException if the stage has closed.
   */
  private synchronized Capture capture(PackResources source, Identifier id) throws IOException {
    if (closed) {
      throw new IOException("Generation stage no longer accepts resource reads");
    }
    return sources.computeIfAbsent(source, ignored -> new HashMap<>()).computeIfAbsent(id, ignored -> new Capture());
  }

  /**
   * Releases the stage's ownership while preserving any outstanding stream leases.
   */
  @Override
  public synchronized void close() {
    closed = true;
    sources.values().forEach(files -> files.values().forEach(capture -> capture.result.thenAccept(file -> file.content.release())));
    sources.clear();
  }

  /**
   * Delegates native pack operations while routing resource reads through the stage.
   */
  private final class Source implements PackResources {
    /**
     * Native pack opening delegated to by this view.
     */
    private final PackResources original;

    /**
     * @param original underlying pack opening.
     */
    private Source(PackResources original) {
      this.original = original;
    }

    @Override
    public PackLocationInfo location() {
      return original.location();
    }

    @Override
    public String packId() {
      return original.packId();
    }

    @Override
    public Optional<KnownPack> knownPackInfo() {
      return original.knownPackInfo();
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... path) {
      return original.getRootResource(path);
    }

    @Override
    public <T> T getMetadataSection(MetadataSectionType<T> type) throws IOException {
      return original.getMetadataSection(type);
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, Identifier id) {
      return wrap(original, type, id, original.getResource(type, id));
    }

    @Override
    public void listResources(PackType type, String namespace, String prefix, ResourceOutput output) {
      original.listResources(type, namespace, prefix, (id, supplier) -> output.accept(id, wrap(original, type, id, supplier)));
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
      return original.getNamespaces(type);
    }

    @Override
    public void close() {}
  }

  /**
   * Coordinates the one-time capture of a source file and shares its result or failure among readers.
   */
  private static final class Capture {
    /**
     * Whether a reader has started copying the source file.
     */
    private final AtomicBoolean started = new AtomicBoolean();

    /**
     * Shared outcome of the capture operation.
     */
    private final CompletableFuture<Captured> result = new CompletableFuture<>();

    /**
     * Captures the bytes once and waits for the same result on subsequent reads.
     *
     * @param source native supplier for the resource.
     * @param limit maximum resource size in bytes.
     * @return immutable captured content and its digest.
     * @throws IOException if the resource cannot be captured or exceeds the limit.
     */
    private Captured open(IoSupplier<InputStream> source, long limit) throws IOException {
      if (started.compareAndSet(false, true)) {
        try {
          var digest = CacheObservations.digest();
          var content = PackFile.write(output -> {
            try (var input = source.get()) {
              PackIO.copy(input, new DigestOutputStream(output, digest), limit);
            }
          });
          result.complete(new Captured(content, CacheObservations.hex(digest)));
        } catch (IOException | RuntimeException | Error failure) {
          result.completeExceptionally(failure);
          throw failure;
        }
      }
      try {
        var captured = result.join();
        if (captured.content.size() > limit) {
          throw new IOException(PackIO.resourceLimitMessage(limit));
        }
        return captured;
      } catch (CompletionException failure) {
        if (failure.getCause() instanceof IOException io) {
          throw io;
        }
        throw failure;
      }
    }
  }

  /**
   * Holds the immutable file backing and the digest computed during its capture.
   *
   * @param content owned backing for captured bytes.
   * @param digest hexadecimal digest of the bytes.
   */
  private record Captured(PackFile content, String digest) {}

  /**
   * Lazily acquires a stream over a shared, captured file.
   */
  static final class SnapshotStream extends InputStream implements PackFile.CapturedStream {
    /**
     * The shared first-read record for this resource.
     */
    private final Capture capture;

    /**
     * Native supplier used if this stream starts the capture.
     */
    private final IoSupplier<InputStream> source;

    /**
     * Completed capture once the stream has initialized.
     */
    private Captured captured;

    /**
     * Reader holding a lease on the captured backing.
     */
    private InputStream input;

    /**
     * Whether this stream has closed and released its lease on the capture.
     */
    private boolean closed;

    /**
     * @param capture shared capture for this resource.
     * @param source supplier for the underlying native file.
     */
    private SnapshotStream(Capture capture, IoSupplier<InputStream> source) {
      this.capture = capture;
      this.source = source;
    }

    /**
     * Opens a reader over the shared capture and checks its size against this read's limit.
     *
     * @param limit maximum allowed resource size.
     * @throws IOException if the stream is closed, capture fails or the resource exceeds the limit.
     */
    void initialize(long limit) throws IOException {
      if (closed) {
        throw new IOException("Snapshot stream has closed");
      }
      if (input == null) {
        captured = capture.open(source, limit);
        input = captured.content.open();
      } else if (captured.content.size() > limit) {
        throw new IOException(PackIO.resourceLimitMessage(limit));
      }
    }

    @Override
    public String digest() throws IOException {
      initialize(Long.MAX_VALUE);
      return captured.digest;
    }

    @Override
    public long size() throws IOException {
      initialize(Long.MAX_VALUE);
      return captured.content.size();
    }

    @Override
    public int read() throws IOException {
      initialize(Long.MAX_VALUE);
      return input.read();
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
      initialize(Long.MAX_VALUE);
      return input.read(bytes, offset, length);
    }

    @Override
    public long skip(long size) throws IOException {
      initialize(Long.MAX_VALUE);
      return input.skip(size);
    }

    @Override
    public void close() throws IOException {
      if (!closed) {
        closed = true;
        if (input != null) {
          input.close();
        }
      }
    }
  }
}
