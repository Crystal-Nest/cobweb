package it.crystalnest.cobweb.api.pack;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import it.crystalnest.cobweb.Constants;
import it.crystalnest.cobweb.platform.Services;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagFile;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/**
 * Stores one validated contribution batch per cached callback or file rule and cache scope.
 */
final class CallbackCache {
  private static final int FORMAT = 3;

  private static final int ENTRY_LIMIT = 32_768;

  private static final long RECORD_LIMIT = 512L * 1024 * 1024;

  private static final long SCOPE_LIMIT = 1024L * 1024 * 1024;

  private static final long LIVE_LIMIT = 128L * 1024 * 1024;

  private static final int LIVE_ENTRIES = 128;

  private static final long ACCESS_REFRESH_MILLIS = TimeUnit.MINUTES.toMillis(1);

  private static final Pattern TEMP_RECORD = Pattern.compile("record-([0-9]{1,18})-[0-9]+\\.tmp");

  private static final String SESSION = UUID.randomUUID().toString();

  private static final Map<Path, String> LATEST = new HashMap<>();

  private static final Map<Path, Batch> BASELINES = new LinkedHashMap<>(16, 0.75F, true);

  private static long baselineBytes;

  private static final ThreadPoolExecutor WRITER = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32), job -> {
    var thread = new Thread(job, "Cobweb Pack Cache");
    thread.setDaemon(true);
    return thread;
  });

  private static boolean environmentChecked;

  @Nullable
  private static String environment;

  private CallbackCache() {}

  static Path address(Path root, GeneratedPackResources pack, ResourcePack.Task task, @Nullable CallbackRegistration.Policy policy) {
    var digest = CacheObservations.digest();
    CacheObservations.text(digest, pack.type.toString());
    CacheObservations.text(digest, pack.packId());
    if (task.fileRule()) {
      CacheObservations.text(digest, "file-rule/" + task.index());
    } else {
      String key = Objects.requireNonNull(policy).key();
      CacheObservations.text(digest, key == null ? SESSION + "/" + task.index() : "explicit/" + key);
    }
    return root.resolve(CacheObservations.hex(digest) + ".packcache").toAbsolutePath().normalize();
  }

  @Nullable
  static String fingerprint(ResourcePack.Task task, GenerationContext context, Map<CapturedInput<?>, Object> values, @Nullable CallbackRegistration.Policy policy, boolean persistent) {
    var digest = CacheObservations.digest();
    String profile = persistent ? environment() : SESSION;
    if (profile == null) {
      return null;
    }
    CacheObservations.text(digest, profile);
    CacheObservations.text(digest, context.phase().name());
    if (!task.fileRule()) {
      CacheObservations.text(digest, context.loadKind().name());
    }
    CacheObservations.text(digest, task.stage().name());
    CacheObservations.text(digest, task.lifetime().name());
    CacheObservations.text(digest, task.outputs().toString());
    if (task.fileRuleFingerprint() != null) {
      CacheObservations.text(digest, task.fileRuleFingerprint());
      return CacheObservations.hex(digest);
    }
    var callbackPolicy = Objects.requireNonNull(policy);
    for (Input input : task.inputs().values()) {
      if (input instanceof CapturedInput<?> capture) {
        if (!value(digest, values.get(capture)) && callbackPolicy.fingerprint() == null) {
          return null;
        }
      } else {
        // Native lookups and implicit codec inputs require their own stable proofs.
        return null;
      }
    }
    if (callbackPolicy.fingerprint() != null && !value(digest, callbackPolicy.fingerprint().apply(context))) {
      return null;
    }
    return CacheObservations.hex(digest);
  }

  private static boolean value(MessageDigest digest, @Nullable Object value) {
    if (value instanceof Enum<?> constant) {
      CacheObservations.text(digest, constant.getDeclaringClass().getName());
      CacheObservations.text(digest, constant.name());
      return true;
    }
    if (value instanceof String || value instanceof Boolean || value instanceof Character || value instanceof Byte || value instanceof Short
      || value instanceof Integer || value instanceof Long || value instanceof Float || value instanceof Double) {
      CacheObservations.text(digest, value.getClass().getName());
      CacheObservations.text(digest, value.toString());
      return true;
    }
    return false;
  }

  @Nullable
  private static synchronized String environment() {
    if (!environmentChecked) {
      environmentChecked = true;
      try {
        var digest = CacheObservations.digest();
        CacheObservations.text(digest, SharedConstants.getCurrentVersion().id());
        CacheObservations.text(digest, Services.PLATFORM.getPlatformName().name());
        CacheObservations.text(digest, System.getProperty("java.runtime.version", ""));
        for (String mod : Services.PLATFORM.loadedModIds()) {
          CacheObservations.text(digest, mod);
          CacheObservations.text(digest, Services.PLATFORM.getModVersion(mod));
        }
        var paths = Services.PLATFORM.modContentPaths().stream().sorted(Comparator.comparing(path -> path.toUri().toString())).toList();
        if (paths.isEmpty()) {
          return null;
        }
        for (Path path : paths) {
          appendArtifact(digest, path);
        }
        environment = CacheObservations.hex(digest);
      } catch (IOException | RuntimeException failure) {
        Constants.LOGGER.warn("Persistent pack caching could not fingerprint loaded code", failure);
      }
    }
    return environment;
  }

  private static void appendArtifact(MessageDigest digest, Path path) throws IOException {
    if (Files.isDirectory(path)) {
      try (var files = Files.walk(path)) {
        for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
          String name = path.relativize(file).toString().replace('\\', '/');
          if (codeFile(name)) {
            try (var bytes = Files.newInputStream(file)) {
              append(digest, name, bytes);
            }
          }
        }
      }
    } else {
      try (var jar = new ZipFile(path.toFile())) {
        for (var entry : jar.stream().filter(item -> !item.isDirectory() && codeFile(item.getName())).sorted(Comparator.comparing(java.util.zip.ZipEntry::getName)).toList()) {
          try (var bytes = jar.getInputStream(entry)) {
            append(digest, entry.getName(), bytes);
          }
        }
      }
    }
  }

  private static boolean codeFile(String name) {
    return name.endsWith(".class") || name.endsWith(".jar") || name.endsWith(".mixins.json") || name.endsWith(".accesswidener") || name.equals("fabric.mod.json") || name.startsWith("META-INF/");
  }

  private static void append(MessageDigest digest, String name, InputStream bytes) throws IOException {
    CacheObservations.text(digest, name);
    var content = CacheObservations.digest();
    bytes.transferTo(new DigestOutputStream(OutputStream.nullOutputStream(), content));
    digest.update(content.digest());
  }

  @Nullable
  static Batch read(Path file, String fingerprint, CacheObservations verifier) {
    Batch current;
    synchronized (LATEST) {
      current = BASELINES.get(file);
      if (current != null) {
        if (!fingerprint.equals(current.fingerprint())) {
          return null;
        }
        current.values().forEach(Contribution::retain);
      }
    }
    if (current != null) {
      boolean valid = false;
      try {
        valid = verifier.matches(current.observations());
        return valid ? current : null;
      } finally {
        if (!valid) {
          current.values().forEach(Contribution::release);
        }
      }
    }
    if (!Files.isRegularFile(file)) {
      return null;
    }
    List<Contribution> values = new ArrayList<>();
    boolean accepted = false;
    try {
      if (Files.size(file) > RECORD_LIMIT) {
        throw new IOException("Cache record exceeds its size limit");
      }
      var digest = CacheObservations.digest();
      try (var source = new DigestInputStream(new BufferedInputStream(Files.newInputStream(file)), digest); var input = new DataInputStream(source)) {
        if (input.readInt() != FORMAT) {
          return null;
        }
        String revision = input.readUTF();
        synchronized (LATEST) {
          if (LATEST.containsKey(file) && !revision.equals(LATEST.get(file))) {
            return null;
          }
        }
        if (!fingerprint.equals(input.readUTF())) {
          return null;
        }
        var observations = new LinkedHashMap<CacheObservations.Request, String>();
        for (int remaining = count(input); remaining > 0; remaining--) {
          var request = new CacheObservations.Request(CacheObservations.Kind.valueOf(input.readUTF()), input.readUTF());
          String saved = input.readUTF();
          if (!saved.equals(verifier.fingerprint(request))) {
            return null;
          }
          observations.put(request, saved);
        }
        for (int remaining = count(input); remaining > 0; remaining--) {
          Identifier id = Identifier.parse(input.readUTF());
          int operation = input.readUnsignedByte();
          if (operation == 0) {
            values.add(Contribution.file(id, readFile(input)));
          } else if (operation == 1) {
            Boolean replace = switch (input.readByte()) {
              case 0 -> null;
              case 1 -> false;
              case 2 -> true;
              default -> throw new IOException("Invalid cached tag operation");
            };
            var tag = TagFile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(input.readUTF())).getOrThrow();
            values.add(Contribution.tag(id, new GenerationOutput.TagAddition(tag.entries(), replace)));
          } else if (operation == 2) {
            var contents = readFile(input);
            try {
              values.add(Contribution.withMetadata(id, contents, input.readBoolean() ? readFile(input) : null));
            } catch (IOException | RuntimeException | Error failure) {
              contents.release();
              throw failure;
            }
          } else if (operation == 3) {
            String member = input.readUTF();
            values.add(Contribution.member(id, member, readFile(input)));
          } else {
            throw new IOException("Invalid cached pack operation");
          }
        }
        source.on(false);
        if (!MessageDigest.isEqual(digest.digest(), input.readNBytes(32)) || input.read() != -1) {
          throw new IOException("Cache record checksum mismatch");
        }
        accepted = true;
        return new Batch(revision, fingerprint, observations, List.copyOf(values));
      }
    } catch (IOException | RuntimeException failure) {
      Constants.LOGGER.warn("Unable to reuse generated pack cache {}", file, failure);
      return null;
    } finally {
      if (!accepted) {
        values.forEach(Contribution::release);
      }
    }
  }

  private static int count(DataInputStream input) throws IOException {
    int count = input.readInt();
    if (count < 0 || count > ENTRY_LIMIT) {
      throw new IOException("Invalid cache entry count");
    }
    return count;
  }

  private static PackFile readFile(DataInputStream input) throws IOException {
    long size = input.readLong();
    if (size < 0 || size > RECORD_LIMIT) {
      throw new IOException("Invalid cached file size");
    }
    return PackFile.write(stream -> copy(input, stream, size));
  }

  private static void copy(InputStream source, OutputStream output, long size) throws IOException {
    byte[] buffer = new byte[8192];
    for (long remaining = size; remaining > 0;) {
      int count = source.readNBytes(buffer, 0, (int) Math.min(buffer.length, remaining));
      if (count == 0) {
        throw new EOFException("Truncated cached file");
      }
      output.write(buffer, 0, count);
      remaining -= count;
    }
  }

  static void apply(Path file, @Nullable Batch batch, boolean hit, boolean persistent) {
    String revision = batch == null ? "" : batch.revision();
    synchronized (LATEST) {
      if (persistent) {
        LATEST.put(file, revision);
      }
      var previous = BASELINES.get(file);
      if (previous != batch) {
        forget(file);
        if (batch != null && batch.bytes() <= LIVE_LIMIT) {
          batch.values().forEach(Contribution::retain);
          BASELINES.put(file, batch);
          baselineBytes += batch.bytes();
          while (baselineBytes > LIVE_LIMIT || BASELINES.size() > LIVE_ENTRIES) {
            forget(BASELINES.keySet().iterator().next());
          }
        }
      }
    }
    if (!persistent) {
      return;
    }
    if (batch != null && !hit) {
      batch.values().forEach(Contribution::retain);
    }
    try {
      WRITER.execute(() -> {
        try {
          synchronized (LATEST) {
            if (!revision.equals(LATEST.get(file))) {
              return;
            }
          }
          if (hit) {
            touch(file, revision);
          } else if (batch == null) {
            Files.deleteIfExists(file);
          } else {
            write(file, batch);
          }
        } catch (IOException | RuntimeException failure) {
          Constants.LOGGER.warn("Unable to persist generated pack cache {}", file, failure);
        } finally {
          if (batch != null && !hit) {
            batch.values().forEach(Contribution::release);
          }
        }
      });
    } catch (RejectedExecutionException busy) {
      Constants.LOGGER.debug("Pack cache writer is busy, skipping persistence of {}", file);
      if (batch != null && !hit) {
        batch.values().forEach(Contribution::release);
      }
    }
  }

  private static void touch(Path file, String revision) throws IOException {
    if (!Files.isRegularFile(file)) {
      return;
    }
    long now = System.currentTimeMillis();
    if (now - Files.getLastModifiedTime(file).toMillis() < ACCESS_REFRESH_MILLIS) {
      return;
    }
    try (var input = new DataInputStream(Files.newInputStream(file))) {
      if (input.readInt() != FORMAT || !revision.equals(input.readUTF())) {
        return;
      }
    }
    Files.setLastModifiedTime(file, FileTime.fromMillis(now));
  }

  private static void forget(Path file) {
    var old = BASELINES.remove(file);
    if (old != null) {
      baselineBytes -= old.bytes();
      old.values().forEach(Contribution::release);
    }
  }

  private static void write(Path file, Batch batch) throws IOException {
    if (batch.bytes() > RECORD_LIMIT) {
      return;
    }
    if (batch.values().size() > ENTRY_LIMIT || batch.observations().size() > ENTRY_LIMIT) {
      return;
    }
    Path directory = Files.createDirectories(file.getParent());
    Path temporary = Files.createTempFile(directory, "record-" + ProcessHandle.current().pid() + "-", ".tmp");
    try {
      var digest = CacheObservations.digest();
      try (var destination = new DigestOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)), digest); var output = new DataOutputStream(destination)) {
        output.writeInt(FORMAT);
        output.writeUTF(batch.revision());
        output.writeUTF(batch.fingerprint());
        output.writeInt(batch.observations().size());
        for (var entry : batch.observations().entrySet()) {
          output.writeUTF(entry.getKey().kind().name());
          output.writeUTF(entry.getKey().key());
          output.writeUTF(entry.getValue());
        }
        output.writeInt(batch.values().size());
        for (Contribution entry : batch.values()) {
          output.writeUTF(entry.id().toString());
          var tag = entry.tag();
          if (tag != null) {
            output.writeByte(1);
            output.writeByte(tag.replace() == null ? 0 : tag.replace() ? 2 : 1);
            output.writeUTF(TagFile.CODEC.encodeStart(JsonOps.INSTANCE, new TagFile(tag.entries(), false)).getOrThrow().toString());
          } else if (entry.groupsMetadata()) {
            output.writeByte(2);
            writeFile(output, entry.file());
            output.writeBoolean(entry.companion() != null);
            if (entry.companion() != null) {
              writeFile(output, entry.companion());
            }
          } else {
            output.writeByte(entry.member() == null ? 0 : 3);
            if (entry.member() != null) {
              output.writeUTF(entry.member());
            }
            writeFile(output, entry.file());
          }
        }
        destination.on(false);
        output.write(digest.digest());
      }
      if (Files.size(temporary) <= RECORD_LIMIT) {
        synchronized (LATEST) {
          if (!batch.revision().equals(LATEST.get(file))) {
            return;
          }
        }
        try {
          Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
          Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
        evict(directory);
      }
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private static void writeFile(DataOutputStream output, PackFile file) throws IOException {
    output.writeLong(file.size());
    try (var bytes = file.open()) {
      bytes.transferTo(output);
    }
  }

  private static void evict(Path directory) throws IOException {
    try (var paths = Files.list(directory)) {
      Map<Path, Long> dates = new HashMap<>();
      Map<Path, Long> sizes = new HashMap<>();
      for (Path path : paths.toList()) {
        var name = path.getFileName().toString();
        if (name.endsWith(".packcache")) {
          dates.put(path, Files.getLastModifiedTime(path).toMillis());
          sizes.put(path, Files.size(path));
        } else {
          var temporary = TEMP_RECORD.matcher(name);
          if (temporary.matches() && ProcessHandle.of(Long.parseLong(temporary.group(1))).filter(ProcessHandle::isAlive).isEmpty()) {
            Files.deleteIfExists(path);
          }
        }
      }
      long total = sizes.values().stream().mapToLong(Long::longValue).sum();
      for (Path path : dates.keySet().stream().sorted(Comparator.comparingLong(dates::get)).toList()) {
        if (total <= SCOPE_LIMIT) {
          break;
        }
        if (Files.deleteIfExists(path)) {
          total -= sizes.get(path);
        }
      }
    }
  }

  record Contribution(Identifier id, @Nullable PackFile file, @Nullable GenerationOutput.TagAddition tag, boolean groupsMetadata, @Nullable PackFile companion, @Nullable String member) {
    static Contribution file(Identifier id, PackFile file) {
      return new Contribution(id, file, null, false, null, null);
    }

    static Contribution tag(Identifier id, GenerationOutput.TagAddition tag) {
      return new Contribution(id, null, tag, false, null, null);
    }

    static Contribution withMetadata(Identifier id, PackFile file, @Nullable PackFile companion) {
      return new Contribution(id, file, null, true, companion, null);
    }

    static Contribution member(Identifier id, String name, PackFile file) {
      return new Contribution(id, file, null, false, null, name);
    }

    Identifier companionId() {
      return id.withPath(path -> path + ".mcmeta");
    }

    void retain() {
      if (file != null) {
        file.retain();
      }
      if (companion != null) {
        companion.retain();
      }
    }

    void release() {
      if (file != null) {
        file.release();
      }
      if (companion != null) {
        companion.release();
      }
    }

    long bytes() {
      return file == null ? 256L + 96L * tag.entries().size() : file.size() + (companion == null ? 0 : companion.size());
    }
  }

  record Batch(String revision, String fingerprint, Map<CacheObservations.Request, String> observations, List<Contribution> values) {
    long bytes() {
      return values.stream().mapToLong(Contribution::bytes).sum();
    }
  }
}
