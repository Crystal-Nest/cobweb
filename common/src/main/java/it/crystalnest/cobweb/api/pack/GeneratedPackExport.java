package it.crystalnest.cobweb.api.pack;

import com.mojang.serialization.JsonOps;
import it.crystalnest.cobweb.Constants;
import it.crystalnest.cobweb.impl.pack.PackMetadataOverrides;
import it.crystalnest.cobweb.impl.pack.NativePackDependencies;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.ApiStatus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.TreeMap;
import java.util.Objects;
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
        .filter(pack -> pack.definition.type() == type && (reference.nativeId() == null ? reference.logicalId().equals(pack.definition.identifier()) : reference.nativeId().equals(pack.packId())))
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
      var type = snapshot.definition.type();
      var section = PackMetadataSection.forPackType(type);
      var metadata = PackMetadataOverrides.readFile(snapshot);
      metadata.remove("overlays");
      metadata.remove("neoforge:overlays");
      metadata.add(section.name(), section.codec().encodeStart(JsonOps.INSTANCE, Objects.requireNonNull(snapshot.getMetadataSection(section))).getOrThrow());
      NativePackDependencies.exportMetadata(metadata, snapshot.definition);
      Files.writeString(staging.resolve("pack.mcmeta"), metadata.toString());
      var icon = snapshot.getRootResource("pack.png");
      if (icon != null) {
        try (var input = icon.get()) {
          Files.copy(input, staging.resolve("pack.png"));
        }
      }
      var directory = staging.resolve(type.getDirectory());
      var files = new TreeMap<Identifier, IoSupplier<InputStream>>();
      snapshot.listAll(type, files::put);
      for (var entry : files.entrySet()) {
        var id = entry.getKey();
        Path file = directory.resolve(id.getNamespace()).resolve(id.getPath());
        if (!file.normalize().equals(file) || !file.startsWith(directory) || file.equals(directory)) {
          throw new IOException("Invalid export path " + id);
        }
        Files.createDirectories(file.getParent());
        try (var input = entry.getValue().get()) {
          Files.copy(input, file);
        }
      }
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

}
