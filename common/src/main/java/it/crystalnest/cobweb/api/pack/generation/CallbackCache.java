package it.crystalnest.cobweb.api.pack.generation;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import it.crystalnest.cobweb.Constants;
import it.crystalnest.cobweb.api.pack.generation.PackGeneration.Task;
import it.crystalnest.cobweb.api.pack.generation.input.CapturedInput;
import it.crystalnest.cobweb.api.pack.generation.input.Input;
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
import java.nio.charset.StandardCharsets;
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
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Stores one validated contribution batch per cached callback or file rule and cache scope.
 */
final class CallbackCache {
  /**
   * Binary format version for persisted contribution batches.
   */
  private static final int FORMAT = 4;

  /**
   * Maximum number of entries accepted in a record count.
   */
  private static final int ENTRY_LIMIT = 32_768;

  /**
   * Maximum byte size for one persisted record.
   */
  private static final long RECORD_LIMIT = 512L * 1024 * 1024;

  /**
   * Maximum aggregate size of records retained in one cache directory.
   */
  private static final long SCOPE_LIMIT = 1024L * 1024 * 1024;

  /**
   * Maximum byte size of in-process baselines.
   */
  private static final long LIVE_LIMIT = 128L * 1024 * 1024;

  /**
   * Maximum number of in-process baselines.
   */
  private static final int LIVE_ENTRIES = 128;

  /**
   * Minimum interval between refreshes of a record's access timestamp.
   */
  private static final long ACCESS_REFRESH_MILLIS = TimeUnit.MINUTES.toMillis(1);

  /**
   * Pattern for temporary record files created by the cache writer.
   */
  private static final Pattern TEMP_RECORD = Pattern.compile("record-(\\d{1,18})-\\d+\\.tmp");

  /**
   * Process-local identifier used by unkeyed Java callbacks.
   */
  private static final String SESSION = UUID.randomUUID().toString();

  /**
   * Latest applied persistent revision by record path, guarded by its monitor.
   */
  private static final Map<Path, String> LATEST = new HashMap<>();

  /**
   * Access-ordered applied batches with retained backing, guarded by the {@link #LATEST} monitor.
   */
  private static final Map<Path, Batch> BASELINES = new LinkedHashMap<>(16, 0.75F, true);

  /**
   * Estimated total size of retained baselines, guarded by the {@link #LATEST} monitor.
   */
  private static long baselineBytes;

  /**
   * Single bounded background worker for cache persistence.
   */
  private static final ThreadPoolExecutor WRITER = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32), job -> {
    var thread = new Thread(job, "Cobweb Pack Cache");
    thread.setDaemon(true);
    return thread;
  });

  /**
   * Whether the persistent environment fingerprint has been attempted.
   */
  private static boolean environmentChecked;

  /**
   * Cached fingerprint of the loaded game, platform and mod artifacts, when available.
   */
  @Nullable
  private static String environment;

  private CallbackCache() {}

  /**
   * Builds a cache address from the native pack and callback or rule identity.
   *
   * @param root physical cache scope.
   * @param pack owning pack.
   * @param task generation task.
   * @param policy Java cache policy, or {@code null} for a file rule.
   * @return normalized absolute record path.
   */
  static Path address(Path root, GeneratedPackResources pack, Task task, @Nullable CallbackRegistration.Policy policy) {
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

  /**
   * Hashes the task contract, execution environment and declared captured inputs.
   *
   * @param task generation task.
   * @param context active callback context.
   * @param values captured input values.
   * @param policy Java cache policy, or {@code null} for a file rule.
   * @param persistent whether the proof should survive a process restart.
   * @return fingerprint, or {@code null} when an input cannot be verified.
   */
  @Nullable
  static String fingerprint(Task task, GenerationContext context, Map<CapturedInput<?>, Object> values, @Nullable CallbackRegistration.Policy policy, boolean persistent) {
    String profile = persistent ? environment() : SESSION;
    if (profile != null) {
      var digest = CacheObservations.digest();
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
      return completeFingerprint(task, context, values, Objects.requireNonNull(policy), digest);
    }
    return null;
  }

  /**
   * Completes a Java callback's fingerprint with its captured values and optional consumer-supplied proof.
   * The digest already contains the shared task and environment contract.
   *
   * @param task Java generation task.
   * @param context active callback context.
   * @param values captured input values.
   * @param policy Java callback cache policy.
   * @param digest digest containing the shared fingerprint prefix.
   * @return complete fingerprint, or {@code null} when an input cannot be verified.
   */
  @Nullable
  private static String completeFingerprint(Task task, GenerationContext context, Map<CapturedInput<?>, Object> values, CallbackRegistration.Policy policy, MessageDigest digest) {
    for (Input input : task.inputs().values()) {
      if (input instanceof CapturedInput<?> capture) {
        if (!value(digest, values.get(capture)) && policy.fingerprint() == null) {
          return null;
        }
      } else {
        // Native lookups and implicit codec inputs require their own stable proofs.
        return null;
      }
    }
    if (policy.fingerprint() != null) {
      Object extra = policy.fingerprint().apply(context);
      if (!value(digest, extra)) {
        Constants.LOGGER.warn("Unsupported pack cache fingerprint type {} for callback {} in {}; return a string, primitive wrapper or enum", extra == null ? "null" : extra.getClass().getName(), task.index(), context.pack());
        return null;
      }
    }
    return CacheObservations.hex(digest);
  }

  /**
   * Hashes a supported Java value with its type information.
   *
   * @param digest digest to update.
   * @param value captured or computed value to hash.
   * @return whether the value has a supported representation.
   */
  private static boolean value(MessageDigest digest, @Nullable Object value) {
    if (value instanceof Enum<?> constant) {
      CacheObservations.text(digest, constant.getDeclaringClass().getName());
      CacheObservations.text(digest, constant.name());
      return true;
    }
    if (value instanceof String || value instanceof Boolean || value instanceof Character || value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long || value instanceof Float || value instanceof Double) {
      CacheObservations.text(digest, value.getClass().getName());
      CacheObservations.text(digest, value.toString());
      return true;
    }
    return false;
  }

  /**
   * Computes or retrieves the persistent cache environment fingerprint.
   *
   * @return hexadecimal fingerprint, or {@code null} when no stable proof is available.
   */
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
        Constants.LOGGER.error("Persistent pack caching could not fingerprint loaded code", failure);
      }
    }
    return environment;
  }

  /**
   * Adds the stable file data from a loaded artifact to the environment fingerprint.
   *
   * @param digest digest to update.
   * @param path path to the mod or game artifact.
   * @throws IOException if the underlying I/O operation fails.
   */
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
        for (var entry : jar.stream().filter(item -> !item.isDirectory() && codeFile(item.getName())).sorted(Comparator.comparing(ZipEntry::getName)).toList()) {
          try (var bytes = jar.getInputStream(entry)) {
            append(digest, entry.getName(), bytes);
          }
        }
      }
    }
  }

  /**
   * Checks whether an artifact entry is relevant to code fingerprinting.
   *
   * @param name relative artifact entry name.
   * @return whether the entry contributes to the environment fingerprint.
   */
  private static boolean codeFile(String name) {
    return name.endsWith(".class") || name.endsWith(".jar") || name.endsWith(".mixins.json") || name.endsWith(".accesswidener") || "fabric.mod.json".equals(name) || name.startsWith("META-INF/");
  }

  /**
   * Adds the name and bytes of an artifact entry to a fingerprint.
   *
   * @param digest digest to update.
   * @param name relative artifact entry name.
   * @param bytes contents of the entry.
   * @throws IOException if the underlying I/O operation fails.
   */
  private static void append(MessageDigest digest, String name, InputStream bytes) throws IOException {
    CacheObservations.text(digest, name);
    var content = CacheObservations.digest();
    bytes.transferTo(new DigestOutputStream(OutputStream.nullOutputStream(), content));
    digest.update(content.digest());
  }

  /**
   * Attempts to reuse a retained or persisted batch after verifying it against the current view.
   *
   * @param file cache record path.
   * @param fingerprint expected callback fingerprint.
   * @param verifier tracker used to verify native observations.
   * @param miss reports why the cached contribution could not be reused.
   * @return retained batch, or {@code null} when no matching record is usable.
   */
  @Nullable
  static Batch read(Path file, String fingerprint, CacheObservations verifier, Consumer<String> miss) {
    Batch current;
    synchronized (LATEST) {
      current = BASELINES.get(file);
      if (current != null) {
        if (!fingerprint.equals(current.fingerprint())) {
          miss.accept("callback fingerprint changed");
          return null;
        }
        current.values().forEach(PackContribution::retain);
      }
    }
    if (current != null) {
      return verifyBaseline(current, verifier, miss);
    }
    if (Files.isRegularFile(file)) {
      return readRecord(file, fingerprint, verifier, miss);
    }
    miss.accept("no previous contribution");
    return null;
  }

  /**
   * Verifies an already-retained baseline and releases it if it cannot be reused.
   * Retaining happens under the baseline monitor before this method is called.
   *
   * @param current retained baseline with a matching callback fingerprint.
   * @param verifier verifies the recorded native inputs.
   * @param miss reports why verification failed.
   * @return retained baseline, or {@code null} when its inputs no longer match.
   */
  @Nullable
  private static Batch verifyBaseline(Batch current, CacheObservations verifier, Consumer<String> miss) {
    boolean valid = false;
    try {
      valid = verifier.matches(current.observations());
      if (!valid) {
        miss.accept("observed inputs changed or could not be verified");
      }
      return valid ? current : null;
    } finally {
      if (!valid) {
        current.values().forEach(PackContribution::release);
      }
    }
  }

  /**
   * Reads one persisted record while owning all partially decoded contributions.
   * Reports failures after the streams close and releases unaccepted backing afterward.
   *
   * @param file existing cache record.
   * @param fingerprint expected callback fingerprint.
   * @param verifier verifies the recorded native inputs.
   * @param miss reports why the record could not be reused.
   * @return decoded batch, or {@code null} when the record is unusable.
   */
  @Nullable
  private static Batch readRecord(Path file, String fingerprint, CacheObservations verifier, Consumer<String> miss) {
    List<PackContribution> values = new ArrayList<>();
    boolean accepted = false;
    try {
      if (Files.size(file) > RECORD_LIMIT) {
        throw new IOException("Cache record exceeds its size limit");
      }
      var digest = CacheObservations.digest();
      try (var source = new DigestInputStream(new BufferedInputStream(Files.newInputStream(file)), digest); var input = new DataInputStream(source)) {
        if (input.readInt() == FORMAT) {
          String revision = input.readUTF();
          synchronized (LATEST) {
            if (LATEST.containsKey(file) && !revision.equals(LATEST.get(file))) {
              miss.accept("a newer contribution superseded the cache record");
              return null;
            }
          }
          boolean fingerprintChanged = !fingerprint.equals(input.readUTF());
          var observations = new LinkedHashMap<CacheObservations.Request, String>();
          if (!fingerprintChanged && readObservations(input, verifier, observations, miss)) {
            for (int remaining = count(input); remaining > 0; remaining--) {
              Identifier id = Identifier.parse(input.readUTF());
              values.add(readContribution(input, id));
            }
            source.on(false);
            if (MessageDigest.isEqual(digest.digest(), input.readNBytes(32)) && input.read() == -1) {
              accepted = true;
              return new Batch(revision, fingerprint, observations, List.copyOf(values));
            }
            throw new IOException("Cache record checksum mismatch");
          }
          if (fingerprintChanged) {
            miss.accept("callback fingerprint changed");
          }
          return null;
        }
        miss.accept("cache format changed");
        return null;
      }
    } catch (IOException | RuntimeException failure) {
      miss.accept("cache record could not be read");
      Constants.LOGGER.error("Unable to reuse generated pack cache {}", file, failure);
      return null;
    } finally {
      if (!accepted) {
        values.forEach(PackContribution::release);
      }
    }
  }

  /**
   * Reads and verifies observations until the first mismatch.
   * Keeps the approved mismatch guard and avoids opening verification for a changed callback fingerprint.
   *
   * @param input open record stream positioned at the observation count.
   * @param verifier verifies each recorded input against the current snapshot.
   * @param observations receives verified input fingerprints.
   * @param miss reports a mismatched or unavailable input.
   * @return whether all recorded observations match.
   * @throws IOException if the observation data cannot be decoded.
   */
  private static boolean readObservations(DataInputStream input, CacheObservations verifier, Map<CacheObservations.Request, String> observations, Consumer<String> miss) throws IOException {
    var verification = verifier.verification();
    for (int remaining = count(input); remaining > 0; remaining--) {
      var request = readRequest(input);
      String saved = input.readUTF();
      if (!saved.equals(verification.fingerprint(request))) {
        miss.accept("observed inputs changed or could not be verified");
        return false;
      }
      observations.put(request, saved);
    }
    return true;
  }

  /**
   * Decodes one contribution using its native cache operation discriminator.
   *
   * @param input cache record stream.
   * @param id destination resource identifier.
   * @return decoded contribution with owned backing.
   * @throws IOException if the operation or its contents are invalid.
   */
  private static PackContribution readContribution(DataInputStream input, Identifier id) throws IOException {
    return switch (input.readUnsignedByte()) {
      case 0 -> PackContribution.file(id, readFile(input));
      case 1 -> PackContribution.tag(id, readTagAddition(input));
      case 2 -> readWithMetadata(input, id);
      case 3 -> PackContribution.member(id, readText(input), readFile(input));
      default -> throw new IOException("Invalid cached pack operation");
    };
  }

  /**
   * Reads cached tag entries and their optional replacement directive.
   *
   * @param input cache record stream positioned at the replacement directive.
   * @return decoded entries with a replacement directive of {@code null} when unspecified.
   * @throws IOException if the directive or text length is invalid, the record is truncated, or reading fails.
   * @throws RuntimeException if the tag JSON cannot be parsed or decoded.
   */
  private static GenerationOutput.TagAddition readTagAddition(DataInputStream input) throws IOException {
    Boolean replace = switch (input.readByte()) {
      case 0 -> null;
      case 1 -> false;
      case 2 -> true;
      default -> throw new IOException("Invalid cached tag operation");
    };
    return new GenerationOutput.TagAddition(TagFile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(readText(input))).getOrThrow().entries(), replace);
  }

  /**
   * Reads a complete file and its optional sidecar as one owned contribution.
   * Releases the first file if the remaining contribution cannot be decoded.
   *
   * @param input cache record stream.
   * @param id destination file identifier.
   * @return contribution owning the decoded backing files.
   * @throws IOException if the record is truncated or invalid.
   */
  private static PackContribution readWithMetadata(DataInputStream input, Identifier id) throws IOException {
    var contents = readFile(input);
    try {
      return PackContribution.withMetadata(id, contents, input.readBoolean() ? readFile(input) : null);
    } catch (IOException | RuntimeException | Error failure) {
      contents.release();
      throw failure;
    }
  }

  /**
   * Reads and validates a bounded record count.
   *
   * @param input cache record stream.
   * @return validated entry count.
   * @throws IOException if the underlying I/O operation fails.
   */
  private static int count(DataInputStream input) throws IOException {
    int count = input.readInt();
    if (count >= 0 && count <= ENTRY_LIMIT) {
      return count;
    }
    throw new IOException("Invalid cache entry count");
  }

  /**
   * Reads one length-prefixed file into managed backing.
   *
   * @param input cache record stream.
   * @return file with one owned lease.
   * @throws IOException if the underlying I/O operation fails.
   */
  private static PackFile readFile(DataInputStream input) throws IOException {
    long size = input.readLong();
    if (size >= 0 && size <= RECORD_LIMIT) {
      return PackFile.write(stream -> copy(input, stream, size));
    }
    throw new IOException("Invalid cached file size");
  }

  /**
   * Copies exactly the declared number of bytes from a cache record.
   *
   * @param source record stream.
   * @param output destination file stream.
   * @param size declared byte count.
   * @throws IOException if the record is truncated or cannot be copied.
   */
  private static void copy(InputStream source, OutputStream output, long size) throws IOException {
    byte[] buffer = new byte[8192];
    long remaining = size;
    while (remaining > 0) {
      int count = source.readNBytes(buffer, 0, (int) Math.min(buffer.length, remaining));
      if (count == 0) {
        throw new EOFException("Truncated cached file");
      }
      output.write(buffer, 0, count);
      remaining -= count;
    }
  }

  /**
   * Updates the latest successfully applied baseline and schedules any persistent work.
   *
   * @param file cache record path.
   * @param batch validated batch, or {@code null} when the applied result cannot be cached.
   * @param hit whether this blob was already reused from cache.
   * @param persistent whether the cache identity persists across launches.
   */
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
          batch.values().forEach(PackContribution::retain);
          BASELINES.put(file, batch);
          baselineBytes += batch.bytes();
          while (baselineBytes > LIVE_LIMIT || BASELINES.size() > LIVE_ENTRIES) {
            forget(BASELINES.keySet().iterator().next());
          }
        }
      }
    }
    if (persistent) {
      if (batch != null && !hit) {
        batch.values().forEach(PackContribution::retain);
      }
      try {
        WRITER.execute(() -> persist(file, batch, hit, revision));
      } catch (RejectedExecutionException _) {
        Constants.LOGGER.debug("Pack cache writer is busy, skipping persistence of {}", file);
        if (batch != null && !hit) {
          batch.values().forEach(PackContribution::release);
        }
      }
    }
  }

  /**
   * Persists the current revision and releases the writer's retained ownership of a newly generated batch.
   * The caller retains the batch before scheduling, and file I/O runs outside the baseline monitor.
   *
   * @param file cache record path.
   * @param batch validated batch, or {@code null} when the persisted result must be removed.
   * @param hit whether the batch was reused and needs only its access time refreshed.
   * @param revision applied revision that must still be current.
   */
  private static void persist(Path file, @Nullable Batch batch, boolean hit, String revision) {
    try {
      boolean currentRevision;
      synchronized (LATEST) {
        currentRevision = revision.equals(LATEST.get(file));
      }
      if (currentRevision) {
        if (hit) {
          touch(file, revision);
        } else if (batch == null) {
          Files.deleteIfExists(file);
        } else {
          write(file, batch);
        }
      }
    } catch (IOException | RuntimeException failure) {
      Constants.LOGGER.error("Unable to persist generated pack cache {}", file, failure);
    } finally {
      if (batch != null && !hit) {
        batch.values().forEach(PackContribution::release);
      }
    }
  }

  /**
   * Refreshes the access time of the matching persisted revision.
   *
   * @param file cache record path.
   * @param revision expected applied revision.
   * @throws IOException if the underlying I/O operation fails.
   */
  private static void touch(Path file, String revision) throws IOException {
    if (Files.isRegularFile(file)) {
      long now = System.currentTimeMillis();
      if (now - Files.getLastModifiedTime(file).toMillis() >= ACCESS_REFRESH_MILLIS) {
        boolean matches;
        try (var input = new DataInputStream(Files.newInputStream(file))) {
          matches = input.readInt() == FORMAT && revision.equals(input.readUTF());
        }
        if (matches) {
          Files.setLastModifiedTime(file, FileTime.fromMillis(now));
        }
      }
    }
  }

  /**
   * Drops an in-process baseline and releases its owned contributions.
   *
   * @param file cache record path.
   */
  private static void forget(Path file) {
    var old = BASELINES.remove(file);
    if (old != null) {
      baselineBytes -= old.bytes();
      old.values().forEach(PackContribution::release);
    }
  }

  /**
   * Writes a verified batch through a temporary record and replaces the persisted revision.
   *
   * @param file cache record path.
   * @param batch applied contribution batch to serialize.
   * @throws IOException if the underlying I/O operation fails.
   */
  private static void write(Path file, Batch batch) throws IOException {
    if (batch.bytes() <= RECORD_LIMIT && batch.values().size() <= ENTRY_LIMIT && batch.observations().size() <= ENTRY_LIMIT) {
      Path directory = Files.createDirectories(file.getParent());
      Path temporary = Files.createTempFile(directory, "record-" + ProcessHandle.current().pid() + "-", ".tmp");
      try {
        writeRecord(temporary, batch);
        if (Files.size(temporary) <= RECORD_LIMIT) {
          boolean currentRevision;
          synchronized (LATEST) {
            currentRevision = batch.revision().equals(LATEST.get(file));
          }
          if (currentRevision) {
            try {
              Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException _) {
              Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            evict(directory);
          }
        }
      } finally {
        Files.deleteIfExists(temporary);
      }
    }
  }

  /**
   * Serializes a complete cache record and closes its output before the caller validates or publishes it.
   * The trailing checksum excludes its own bytes from the digest.
   *
   * @param temporary temporary record path.
   * @param batch applied contribution batch to serialize.
   * @throws IOException if opening, encoding or closing the record fails.
   */
  private static void writeRecord(Path temporary, Batch batch) throws IOException {
    var digest = CacheObservations.digest();
    try (var destination = new DigestOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)), digest); var output = new DataOutputStream(destination)) {
      output.writeInt(FORMAT);
      output.writeUTF(batch.revision());
      output.writeUTF(batch.fingerprint());
      output.writeInt(batch.observations().size());
      for (var entry : batch.observations().entrySet()) {
        writeRequest(output, entry.getKey());
        output.writeUTF(entry.getValue());
      }
      output.writeInt(batch.values().size());
      for (PackContribution entry : batch.values()) {
        output.writeUTF(entry.id().toString());
        switch (entry) {
          case PackContribution.File fileEntry -> {
            output.writeByte(0);
            writeFile(output, fileEntry.contents());
          }
          case PackContribution.Tag tagEntry -> {
            var tag = tagEntry.addition();
            output.writeByte(1);
            int replace = 0;
            if (tag.replace() != null) {
              replace = tag.replace() ? 2 : 1;
            }
            output.writeByte(replace);
            writeText(output, TagFile.CODEC.encodeStart(JsonOps.INSTANCE, new TagFile(tag.entries(), false)).getOrThrow().toString());
          }
          case PackContribution.WithMetadata grouped -> {
            output.writeByte(2);
            writeFile(output, grouped.contents());
            output.writeBoolean(grouped.metadata() != null);
            if (grouped.metadata() != null) {
              writeFile(output, grouped.metadata());
            }
          }
          case PackContribution.Member member -> {
            output.writeByte(3);
            writeText(output, member.name());
            writeFile(output, member.contents());
          }
        }
      }
      destination.on(false);
      output.write(digest.digest());
    }
  }

  /**
   * Writes a native observation address to a cache record.
   *
   * @param output cache record stream.
   * @param request observed query or file address.
   * @throws IOException if the underlying I/O operation fails.
   */
  private static void writeRequest(DataOutputStream output, CacheObservations.Request request) throws IOException {
    output.writeUTF(request.kind().name());
    if (request.file() == null) {
      writeText(output, request.key());
    } else {
      var file = request.file();
      output.writeUTF(file.id().toString());
      output.writeInt(file.stackIndex());
      output.writeLong(file.maxBytes());
      output.writeBoolean(file.directory() != null);
      if (file.directory() != null) {
        writeText(output, file.directory());
      }
    }
  }

  /**
   * Decodes a native observation address from a cache record.
   *
   * @param input cache record stream.
   * @return observation address.
   * @throws IOException if the underlying I/O operation fails.
   */
  private static CacheObservations.Request readRequest(DataInputStream input) throws IOException {
    var kind = CacheObservations.Kind.valueOf(input.readUTF());
    if (kind == CacheObservations.Kind.CONTENT || kind == CacheObservations.Kind.METADATA) {
      var id = Identifier.parse(input.readUTF());
      int index = input.readInt();
      long maxBytes = input.readLong();
      String directory = input.readBoolean() ? readText(input) : null;
      return new CacheObservations.Request(kind, new CacheObservations.FileAddress(id, directory, index, maxBytes));
    }
    return new CacheObservations.Request(kind, readText(input));
  }

  /**
   * Writes a length-prefixed UTF-8 string to a cache record.
   *
   * @param output cache record stream.
   * @param value string to encode.
   * @throws IOException if the underlying I/O operation fails.
   */
  private static void writeText(DataOutputStream output, String value) throws IOException {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    if (bytes.length > RECORD_LIMIT) {
      throw new IOException("Cached text exceeds its size limit");
    }
    output.writeInt(bytes.length);
    output.write(bytes);
  }

  /**
   * Reads a bounded length-prefixed UTF-8 string from a cache record.
   *
   * @param input cache record stream.
   * @return decoded string.
   * @throws IOException if the underlying I/O operation fails.
   */
  private static String readText(DataInputStream input) throws IOException {
    int length = input.readInt();
    if (length >= 0 && length <= RECORD_LIMIT) {
      byte[] bytes = input.readNBytes(length);
      if (bytes.length == length) {
        return new String(bytes, StandardCharsets.UTF_8);
      }
      throw new EOFException("Truncated cached text");
    }
    throw new IOException("Invalid cached text size");
  }

  /**
   * Writes a file's length and immutable bytes to a cache record.
   *
   * @param output cache record stream.
   * @param file managed file to serialize.
   * @throws IOException if the underlying I/O operation fails.
   */
  private static void writeFile(DataOutputStream output, PackFile file) throws IOException {
    output.writeLong(file.size());
    try (var bytes = file.open()) {
      bytes.transferTo(output);
    }
  }

  /**
   * Evicts old records to keep a cache directory within its size limit.
   *
   * @param directory physical cache scope.
   * @throws IOException if the underlying I/O operation fails.
   */
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
      var oldest = dates.keySet().stream().sorted(Comparator.comparingLong(dates::get)).iterator();
      while (total > SCOPE_LIMIT && oldest.hasNext()) {
        Path path = oldest.next();
        if (Files.deleteIfExists(path)) {
          total -= sizes.get(path);
        }
      }
    }
  }

  /**
   * Stores one contribution batch and the proofs needed to reuse it.
   *
   * @param revision unique revision of the applied batch.
   * @param fingerprint callback and environment fingerprint.
   * @param observations native observations to verify.
   * @param values complete generated contributions.
   */
  record Batch(String revision, String fingerprint, Map<CacheObservations.Request, String> observations, List<PackContribution> values) {
    /**
     * Returns the estimated size of the stored contributions.
     *
     * @return size in bytes.
     */
    long bytes() {
      return values.stream().mapToLong(PackContribution::bytes).sum();
    }
  }
}
