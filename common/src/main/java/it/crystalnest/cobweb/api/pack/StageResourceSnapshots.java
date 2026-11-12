package it.crystalnest.cobweb.api.pack;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.Nullable;

import java.io.FilterInputStream;
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
  private final PackType type;

  private final Map<PackResources, Map<Identifier, Capture>> sources = new IdentityHashMap<>();

  private boolean closed;

  StageResourceSnapshots(PackType type) {
    this.type = type;
  }

  PackResources wrap(PackResources source) {
    return new Source(source);
  }

  @Nullable
  IoSupplier<InputStream> wrap(PackResources source, PackType type, Identifier id, @Nullable IoSupplier<InputStream> original) {
    return original == null || type != this.type ? original : () -> capture(source, id).open(original);
  }

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

  private final class Source implements PackResources {
    private final PackResources original;

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

  private static final class Capture {
    private final AtomicBoolean started = new AtomicBoolean();

    private final CompletableFuture<Captured> result = new CompletableFuture<>();

    private InputStream open(IoSupplier<InputStream> source) throws IOException {
      if (started.compareAndSet(false, true)) {
        try {
          var digest = CacheObservations.digest();
          var content = PackFile.write(output -> {
            try (var input = source.get()) {
              input.transferTo(new DigestOutputStream(output, digest));
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
        return new SnapshotStream(captured.content.open(), captured.digest);
      } catch (CompletionException failure) {
        if (failure.getCause() instanceof IOException io) {
          throw io;
        }
        throw failure;
      }
    }
  }

  private record Captured(PackFile content, String digest) {}

  static final class SnapshotStream extends FilterInputStream {
    final String digest;

    private SnapshotStream(InputStream input, String digest) {
      super(input);
      this.digest = digest;
    }
  }
}
