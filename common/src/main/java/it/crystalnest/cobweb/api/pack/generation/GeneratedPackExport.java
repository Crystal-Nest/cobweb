package it.crystalnest.cobweb.api.pack.generation;

import com.mojang.serialization.JsonOps;
import it.crystalnest.cobweb.Constants;
import it.crystalnest.cobweb.api.pack.dependency.NativePackDependencies;
import it.crystalnest.cobweb.platform.services.PackHelper;
import it.crystalnest.cobweb.api.pack.query.PackReference;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.ApiStatus;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Copies a retained generated resource opening without reading or reloading unrelated packs.
 */
@ApiStatus.Internal
public final class GeneratedPackExport {
  private static final ThreadPoolExecutor WRITER = new ThreadPoolExecutor(0, 1, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(4), task -> {
    var thread = new Thread(task, "Cobweb pack export");
    thread.setDaemon(true);
    return thread;
  });

  private GeneratedPackExport() {}

  /**
   * Pins an applied resource opening and queues its export.
   *
   * @param manager live native resource manager.
   * @param type pack kind.
   * @param reference identity to export.
   * @return export result.
   */
  public static CompletionStage<Path> write(ResourceManager manager, PackType type, PackReference reference) {
    try {
      var matches = manager.listPacks()
        .filter(GeneratedPackResources.class::isInstance)
        .map(GeneratedPackResources.class::cast)
        .filter(pack -> pack.definition != null && pack.type == type && (reference.nativeId() == null ? reference.logicalId().equals(pack.logicalId) : reference.nativeId().equals(pack.packId())))
        .toList();
      if (matches.size() != 1) {
        return CompletableFuture.failedFuture(new IllegalArgumentException("Expected one applied Cobweb pack for " + reference + ", found " + matches.size()));
      }
      var snapshot = matches.getFirst().retainAppliedSnapshot();
      var result = new CompletableFuture<Path>();
      try {
        WRITER.execute(() -> {
          try {
            Path path;
            try (snapshot) {
              path = copy(snapshot);
            }
            result.complete(path);
          } catch (IOException | RuntimeException failure) {
            result.completeExceptionally(failure);
          }
        });
      } catch (RuntimeException failure) {
        snapshot.close();
        result.completeExceptionally(failure);
      }
      return result.minimalCompletionStage();
    } catch (RuntimeException failure) {
      return CompletableFuture.failedFuture(failure);
    }
  }

  private static Path copy(GeneratedPackResources snapshot) throws IOException {
    Path root = Files.createDirectories(Path.of("cobweb", "exports")).toAbsolutePath().normalize();
    Path staging = Files.createTempDirectory(root, ".pack-");
    try {
      writeContents(snapshot, true, (name, input) -> {
        var file = staging.resolve(name);
        Files.createDirectories(file.getParent());
        Files.copy(input, file);
      });
      Path destination = root.resolve("pack-" + staging.getFileName().toString().substring(".pack-".length()));
      try {
        return Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException ignored) {
        return Files.move(staging, destination);
      }
    } finally {
      if (Files.exists(staging)) {
        try (var paths = Files.walk(staging)) {
          for (var path : paths.sorted(Comparator.reverseOrder()).toList()) {
            Files.deleteIfExists(path);
          }
        } catch (IOException failure) {
          Constants.LOGGER.warn("Unable to remove unfinished pack export {}", staging, failure);
        }
      }
    }
  }

  static void writeContents(GeneratedPackResources snapshot, boolean inspection, EntryWriter writer) throws IOException {
    var type = snapshot.definition.type();
    var section = PackMetadataSection.forPackType(type);
    var metadata = PackHelper.readMetadataFile(snapshot);
    metadata.remove("overlays");
    metadata.remove("neoforge:overlays");
    metadata.add(section.name(), section.codec().encodeStart(JsonOps.INSTANCE, Objects.requireNonNull(snapshot.getMetadataSection(section))).getOrThrow());
    if (inspection) {
      NativePackDependencies.exportMetadata(metadata, snapshot.definition);
    } else {
      // The downloaded copy can coexist with a locally registered pack of the same logical ID.
      metadata.remove("cobweb");
    }
    try (var input = new ByteArrayInputStream(metadata.toString().getBytes(StandardCharsets.UTF_8))) {
      writer.write("pack.mcmeta", input);
    }
    var icon = snapshot.getRootResource("pack.png");
    if (icon != null) {
      try (var input = icon.get()) {
        writer.write("pack.png", input);
      }
    }
    var files = new TreeMap<Identifier, IoSupplier<InputStream>>();
    snapshot.listAll(type, files::put);
    for (var entry : files.entrySet()) {
      var path = PackPaths.resourcePath(type, entry.getKey());
      try (var input = entry.getValue().get()) {
        writer.write(path, input);
      }
    }
  }

  @FunctionalInterface
  interface EntryWriter {
    void write(String path, InputStream input) throws IOException;
  }
}
