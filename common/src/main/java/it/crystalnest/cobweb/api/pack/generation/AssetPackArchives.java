package it.crystalnest.cobweb.api.pack.generation;

import it.crystalnest.cobweb.Constants;
import it.crystalnest.cobweb.api.pack.AssetPack;
import it.crystalnest.cobweb.platform.Services;
import it.crystalnest.cobweb.platform.services.PackHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackResources;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Captures one detached publication and writes its deterministic native ZIPs.
 */
@ApiStatus.Internal
public final class AssetPackArchives {
  private static final long MAX_ZIP_BYTES = 250L * 1024 * 1024;

  private static final long MAX_CONTENT_BYTES = 1024L * 1024 * 1024;

  private static final int MAX_ENTRIES = 65_535;

  private static final LocalDateTime ZIP_TIME = LocalDateTime.of(2000, 1, 1, 0, 0);

  private AssetPackArchives() {}

  /**
   * Captures the applied server state needed for publication.
   *
   * @param server server on its main thread after a successful load.
   * @param packs selected asset pack registrations in increasing priority.
   * @param warnClientCallbacks whether to report client-only output.
   * @return detached publication job.
   */
  public static Build capture(MinecraftServer server, List<PackHelper.RegisteredPack> packs, boolean warnClientCallbacks) {
    return capture(server, packs, warnClientCallbacks, new Reuse(), () -> true);
  }

  /**
   * Captures a cancellable publication with reusable static archives.
   *
   * @param server server on its main thread after a successful load.
   * @param packs selected asset pack registrations in increasing priority.
   * @param warnClientCallbacks whether to report client-only output.
   * @param reuse archive records owned by this publisher.
   * @param current whether the requested revision is still current.
   * @return detached publication job.
   */
  public static Build capture(MinecraftServer server, List<PackHelper.RegisteredPack> packs, boolean warnClientCallbacks, Reuse reuse, BooleanSupplier current) {
    var captures = new ArrayList<Captured>();
    for (var registration : packs) {
      var callbacks = registration.publication();
      if (callbacks == null && warnClientCallbacks && registration.generation().generates()) {
        Constants.LOGGER.warn("Publishing only bundled assets of {} because its callbacks run on the client", registration.pack().identifier());
      }
      captures.add(new Captured(registration, callbacks == null ? null : CapturedPublication.capture(server, callbacks)));
    }
    return (directory, staging) -> write(directory, staging, captures, reuse, current);
  }

  private static List<Archive> write(Path directory, Path staging, List<Captured> captures, Reuse reuse, BooleanSupplier current) throws IOException {
    Files.createDirectories(directory);
    Files.createDirectories(staging);
    var resources = new ArrayList<PackResources>();
    try {
      for (var capture : captures) {
        checkCurrent(current);
        var nativePack = Services.PACK.toPack(capture.registration);
        if (nativePack == null || !nativePack.getCompatibility().isCompatible()) {
          throw new IOException("Invalid or incompatible asset pack metadata for " + capture.registration.pack().identifier());
        }
        try (var openings = nativePack.open()) {
          openings.forEach(resources::add);
        }
      }
      Services.PACK.validatePublicationDependencies(resources);
      var archives = new ArrayList<Archive>();
      for (int index = 0; index < resources.size(); index++) {
        checkCurrent(current);
        var capture = captures.get(index);
        var id = capture.registration.pack().identifier();
        var pack = (GeneratedPackResources) resources.get(index);
        if (capture.publication != null) {
          var output = new AssetPublicationOutput(pack);
          try {
            capture.publication.generate(output);
            output.finish();
          } finally {
            output.close();
          }
        }
        var previous = capture.publication == null ? reuse.staticArchives.get(id) : null;
        if (previous != null && previous.contents.equals(fingerprint(pack, current)) && intact(previous.archive, current)) {
          archives.add(previous.archive);
        } else {
          var written = writeArchive(directory, staging, id, pack, current);
          archives.add(written.archive);
          if (capture.publication == null) {
            reuse.staticArchives.put(id, written);
          }
        }
      }
      return List.copyOf(archives);
    } finally {
      resources.forEach(PackResources::close);
    }
  }

  /**
   * Writes a publication ZIP and derives its stable network offer ID from the registered pack identifier.
   *
   * @param directory publication storage directory.
   * @param scratch temporary storage on the same file system.
   * @param id registered asset pack identifier.
   * @param pack opened resources to archive.
   * @param current whether the requested revision is still current.
   * @return archive and its content fingerprint.
   * @throws IOException if reading or writing the archive fails.
   */
  private static CachedArchive writeArchive(Path directory, Path scratch, Identifier id, GeneratedPackResources pack, BooleanSupplier current) throws IOException {
    checkCurrent(current);
    var contentHash = CacheObservations.digest();
    Path staging = Files.createTempFile(scratch, ".pending-", ".zip");
    var sha1 = digest("SHA-1");
    var sha256 = digest("SHA-256");
    try {
      try (var file = Files.newOutputStream(staging);
           var bounded = new LimitedOutput(file, MAX_ZIP_BYTES);
           var first = new DigestOutputStream(bounded, sha1);
           var second = new DigestOutputStream(first, sha256);
           var zip = new ZipOutputStream(second)) {
        var contents = new LimitedOutput(zip, MAX_CONTENT_BYTES);
        int[] entries = {0};
        GeneratedPackExport.writeContents(pack, false, (name, input) -> {
          if (++entries[0] > MAX_ENTRIES) {
            throw new IOException("Too many files in published asset pack " + pack.packId());
          }
          var entry = new ZipEntry(name);
          entry.setTimeLocal(ZIP_TIME);
          zip.putNextEntry(entry);
          CacheObservations.text(contentHash, name);
          var entryHash = CacheObservations.digest();
          copy(input, new DigestOutputStream(contents, entryHash), current);
          CacheObservations.text(contentHash, CacheObservations.hex(entryHash));
          zip.closeEntry();
        });
      }
      checkCurrent(current);
      String address = HexFormat.of().formatHex(sha256.digest());
      Path destination = directory.resolve(address + ".zip");
      long size = Files.size(staging);
      if (!Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS) || Files.mismatch(staging, destination) != -1) {
        try {
          Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
          Files.move(staging, destination, StandardCopyOption.REPLACE_EXISTING);
        }
      }
      var uuid = UUID.nameUUIDFromBytes(("cobweb:published-assets/" + id).getBytes(StandardCharsets.UTF_8));
      return new CachedArchive(CacheObservations.hex(contentHash), new Archive(id, uuid, address, HexFormat.of().formatHex(sha1.digest()), destination, size));
    } finally {
      Files.deleteIfExists(staging);
    }
  }

  private static String fingerprint(GeneratedPackResources pack, BooleanSupplier current) throws IOException {
    var digest = CacheObservations.digest();
    var bounded = new LimitedOutput(OutputStream.nullOutputStream(), MAX_CONTENT_BYTES);
    int[] entries = {0};
    GeneratedPackExport.writeContents(pack, false, (name, input) -> {
      if (++entries[0] > MAX_ENTRIES) {
        throw new IOException("Too many files in published asset pack " + pack.packId());
      }
      CacheObservations.text(digest, name);
      var content = CacheObservations.digest();
      copy(input, new DigestOutputStream(bounded, content), current);
      CacheObservations.text(digest, CacheObservations.hex(content));
    });
    return CacheObservations.hex(digest);
  }

  private static boolean intact(Archive archive, BooleanSupplier current) throws IOException {
    if (!Files.isRegularFile(archive.path(), LinkOption.NOFOLLOW_LINKS) || Files.size(archive.path()) != archive.size()) {
      return false;
    }
    var digest = CacheObservations.digest();
    try (var input = Files.newInputStream(archive.path(), LinkOption.NOFOLLOW_LINKS)) {
      copy(input, new DigestOutputStream(OutputStream.nullOutputStream(), digest), current);
    }
    return archive.address().equals(CacheObservations.hex(digest));
  }

  private static void copy(InputStream input, OutputStream output, BooleanSupplier current) throws IOException {
    byte[] buffer = new byte[8192];
    while (true) {
      checkCurrent(current);
      int count = input.read(buffer);
      if (count < 0) {
        return;
      }
      output.write(buffer, 0, count);
    }
  }

  private static void checkCurrent(BooleanSupplier current) {
    if (!current.getAsBoolean() || Thread.currentThread().isInterrupted()) {
      throw new CancellationException("Asset publication superseded or stopped");
    }
  }

  /**
   * Retains static archive fingerprints for one serial publication worker.
   */
  public static final class Reuse {
    private final Map<Identifier, CachedArchive> staticArchives = new HashMap<>();

    /**
     * Creates an empty publisher-owned archive index.
     */
    public Reuse() {}
  }

  private record CachedArchive(String contents, Archive archive) {}

  private static MessageDigest digest(String algorithm) {
    try {
      return MessageDigest.getInstance(algorithm);
    } catch (NoSuchAlgorithmException failure) {
      throw new IllegalStateException("Missing ZIP digest " + algorithm, failure);
    }
  }

  /**
   * Writes an archive set using previously captured values.
   */
  @FunctionalInterface
  public interface Build {
    /**
     * Builds a complete candidate publication.
     *
     * @param directory publication storage directory.
     * @param staging temporary storage on the same file system.
     * @return archives in increasing priority.
     * @throws IOException if opening, generating or writing a pack fails.
     */
    List<Archive> write(Path directory, Path staging) throws IOException;
  }

  /**
   * Identifies the immutable bytes of a published pack.
   *
   * @param id logical pack ID.
   * @param uuid stable network offer ID.
   * @param address SHA-256 digest used in its route.
   * @param sha1 native download hash.
   * @param path archived file.
   * @param size archived file size in bytes.
   */
  public record Archive(Identifier id, UUID uuid, String address, String sha1, Path path, long size) {}

  /**
   * Holds a pack and its optional worker input for one publication.
   *
   * @param registration asset pack registration.
   * @param publication captured callback inputs and worker callback, or {@code null} if absent.
   */
  private record Captured(PackHelper.RegisteredPack registration, @Nullable CapturedPublication<?> publication) {}

  /**
   * Holds a callback and its detached inputs for one archive build.
   *
   * @param inputs value produced on the server thread.
   * @param callback generation callback to run on the publication worker.
   * @param <T> detached capture type.
   */
  private record CapturedPublication<T>(T inputs, AssetPack.PublicationCallback<T> callback) {
    /**
     * Reads the inputs for a publication on the server thread.
     *
     * @param server server whose applied state the capture can read.
     * @param callbacks capture and generation callbacks for this pack.
     * @param <T> detached capture type.
     * @return inputs paired with their generation callback.
     */
    private static <T> CapturedPublication<T> capture(MinecraftServer server, PackHelper.PublicationCallbacks<T> callbacks) {
      return new CapturedPublication<>(callbacks.capture().apply(server), callbacks.generate());
    }

    /**
     * Generates the pack's assets on the publication worker.
     *
     * @param output access to this pack's bundled assets and generated output.
     * @throws IOException if reading or writing an asset fails.
     */
    private void generate(AssetPublicationOutput output) throws IOException {
      callback.generate(inputs, output);
    }
  }

  private static final class LimitedOutput extends FilterOutputStream {
    private long remaining;

    private LimitedOutput(OutputStream output, long limit) {
      super(output);
      remaining = limit;
    }

    @Override
    public void write(int value) throws IOException {
      reserve(1);
      out.write(value);
    }

    @Override
    public void write(byte @NonNull [] bytes, int offset, int length) throws IOException {
      reserve(length);
      out.write(bytes, offset, length);
    }

    private void reserve(int length) throws IOException {
      if (length > remaining) {
        throw new IOException("Published asset pack exceeds its size limit");
      }
      remaining -= length;
    }
  }
}
