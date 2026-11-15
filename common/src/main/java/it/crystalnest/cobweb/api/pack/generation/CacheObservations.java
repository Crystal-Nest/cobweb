package it.crystalnest.cobweb.api.pack.generation;

import it.crystalnest.cobweb.api.pack.query.PackQueries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Records native resource observations that a cache hit must prove again.
 */
final class CacheObservations {
  /**
   * Native view used to resolve observations during verification.
   */
  private final ResourceManager manager;

  /**
   * Pack selection and priority snapshot for this attempt.
   */
  private final PackQueries packs;

  /**
   * Enabled feature flags for this attempt.
   */
  private final FeatureFlagSet features;

  /**
   * Fingerprints recorded by this callback, including reads from its async work.
   */
  private final Map<Request, String> entries = new ConcurrentHashMap<>();

  /**
   * Whether all observed inputs can still be verified.
   */
  private volatile boolean supported = true;

  /**
   * @param manager stable native resource view.
   * @param packs pack selection and priority snapshot.
   * @param features active feature flags.
   * @param enabled whether callback caching was requested.
   */
  CacheObservations(ResourceManager manager, PackQueries packs, FeatureFlagSet features, boolean enabled) {
    this.manager = manager;
    this.packs = packs;
    this.features = features;
    supported = enabled;
  }

  /**
   * Records the fingerprint of a native query.
   *
   * @param kind query kind.
   * @param id query argument.
   */
  void observe(Kind kind, String id) {
    if (supported) {
      try {
        var request = new Request(kind, id);
        record(request, fingerprint(request));
      } catch (IOException | RuntimeException failure) {
        supported = false;
      }
    }
  }

  /**
   * Records the fingerprint of a query result already computed by the caller.
   *
   * @param kind query kind.
   * @param key query argument.
   * @param append operation that hashes the observed result.
   */
  void observe(Kind kind, String key, Consumer<MessageDigest> append) {
    if (supported) {
      try {
        var digest = digest();
        append.accept(digest);
        record(new Request(kind, key), hex(digest));
      } catch (RuntimeException failure) {
        supported = false;
      }
    }
  }

  /**
   * Records the content fingerprint from the same capture the callback reads.
   *
   * @param id resolved file identifier.
   * @param directory listing directory, or {@code null} for a direct lookup.
   * @param stackIndex position in the native stack, or {@code -1} for the winner.
   * @param maxBytes maximum size allowed for this read.
   * @param resource resolved resource to fingerprint.
   * @param opened stream returned to the caller.
   */
  void observeContent(Identifier id, @Nullable String directory, int stackIndex, long maxBytes, Resource resource, InputStream opened) {
    if (supported) {
      try {
        var request = new Request(Kind.CONTENT, new FileAddress(id, directory, stackIndex, maxBytes));
        var digest = digest();
        if (opened instanceof PackFile.CapturedStream) {
          appendContent(digest, resource, opened);
        } else {
          // Native stream decorators and immutable generated files can be opened again within the same stage view.
          try (var input = PackIO.open(resource, maxBytes)) {
            appendContent(digest, resource, input);
          }
        }
        record(request, hex(digest));
      } catch (IOException | RuntimeException failure) {
        supported = false;
      }
    }
  }

  /**
   * Records the effective native sidecar, including its absence.
   *
   * @param id resolved file identifier.
   * @param directory listing directory, or {@code null} for a direct lookup.
   * @param stackIndex position in the native stack, or {@code -1} for the winner.
   * @param metadata effective metadata returned by Minecraft.
   */
  void observeMetadata(Identifier id, @Nullable String directory, int stackIndex, ResourceMetadata metadata) {
    if (!supported) {
      return;
    }
    try {
      record(new Request(Kind.METADATA, new FileAddress(id, directory, stackIndex, Long.MAX_VALUE)), metadataFingerprint(metadata));
    } catch (IOException | RuntimeException failure) {
      supported = false;
    }
  }

  /**
   * Records an observation and disables reuse if the same request gave different results.
   *
   * @param request description of the observed query.
   * @param fingerprint fingerprint of the actual result.
   */
  private void record(Request request, String fingerprint) {
    var previous = entries.putIfAbsent(request, fingerprint);
    if (previous != null && !previous.equals(fingerprint)) {
      supported = false;
    }
  }

  /**
   * Disables caching for the rest of this callback.
   */
  void unsupported() {
    supported = false;
  }

  /**
   * Indicates whether this callback is still tracking its reads.
   *
   * @return whether observations can still support reuse.
   */
  boolean tracking() {
    return supported;
  }

  /**
   * Returns the observations made so far.
   *
   * @return immutable observation fingerprints.
   */
  Map<Request, String> entries() {
    return Map.copyOf(entries);
  }

  /**
   * Checks that this callback's observations can be reproduced.
   *
   * @return whether all observations are stable.
   */
  boolean validate() {
    return supported && matches(entries);
  }

  /**
   * Rechecks recorded observations against the current native view.
   *
   * @param previous observations from a generated or cached batch.
   * @return whether every recorded fingerprint still matches.
   */
  boolean matches(Map<Request, String> previous) {
    try {
      var verification = new Verification();
      for (var entry : previous.entrySet()) {
        if (!entry.getValue().equals(verification.fingerprint(entry.getKey()))) {
          return false;
        }
      }
      return true;
    } catch (IOException | RuntimeException failure) {
      return false;
    }
  }

  /**
   * Computes a query proof against the current native resource view.
   *
   * @param request description of the query to reproduce.
   * @return hexadecimal fingerprint of the current result.
   * @throws IOException if the underlying I/O operation fails.
   */
  String fingerprint(Request request) throws IOException {
    return new Verification().fingerprint(request);
  }

  /**
   * Starts a verifier with its own memoized native listings.
   *
   * @return verifier scoped to this resource view.
   */
  Verification verification() { return new Verification(); }

  /**
   * Replays native queries while sharing listings within one verification.
   */
  final class Verification {
    /**
     * Memoized winning resources by directory.
     */
    private final Map<String, Map<Identifier, Resource>> listings = new HashMap<>();

    /**
     * Memoized resource stacks by directory.
     */
    private final Map<String, Map<Identifier, List<Resource>>> stackListings = new HashMap<>();

    /**
     * Runs or reuses a native winning-resource listing.
     *
     * @param directory directory to use.
     * @return winning resource per identifier.
     */
    private Map<Identifier, Resource> listing(String directory) {
      return listings.computeIfAbsent(directory, path -> manager.listResources(path, id -> true));
    }

    /**
     * Runs or reuses a native stack listing.
     *
     * @param directory directory to use.
     * @return resource stack per identifier in native priority order.
     */
    private Map<Identifier, List<Resource>> stacks(String directory) {
      return stackListings.computeIfAbsent(directory, path -> manager.listResourceStacks(path, id -> true));
    }

    /**
     * Computes a query proof against the current native resource view.
     *
     * @param request description of the query to reproduce.
     * @return hexadecimal fingerprint of the current result.
     * @throws IOException if the underlying I/O operation fails.
     */
    String fingerprint(Request request) throws IOException {
      var digest = digest();
      switch (request.kind()) {
        case RESOURCE -> manager.getResource(Identifier.parse(request.key())).ifPresent(resource -> text(digest, resource.sourcePackId()));
        case STACK -> appendSources(digest, manager.getResourceStack(Identifier.parse(request.key())));
        case LIST -> appendList(digest, listing(request.key()));
        case LIST_STACKS -> appendStacks(digest, stacks(request.key()));
        case NAMESPACES -> manager.getNamespaces().stream().sorted().forEach(namespace -> text(digest, namespace));
        case PACKS -> text(digest, packs.toString());
        case FEATURES -> FeatureFlags.REGISTRY.toNames(features).stream().sorted().forEach(flag -> text(digest, flag.toString()));
        case CONTENT -> {
          var resource = fileResource(request.file());
          if (resource != null) {
            try (var input = PackIO.open(resource, request.file().maxBytes())) {
              PackIO.checkLimit(input, request.file().maxBytes());
              appendContent(digest, resource, input);
            }
          }
        }
        case METADATA -> {
          var resource = fileResource(request.file());
          return resource == null ? hex(digest) : metadataFingerprint(resource.metadata());
        }
      }
      return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Resolves the same lookup or listing position that supplied a file handle.
     *
     * @param key address of the original file observation.
     * @return resolved native resource, or {@code null} if the file or stack position is absent.
     */
    @Nullable
    private Resource fileResource(FileAddress key) {
      int index = key.stackIndex();
      var id = key.id();
      String directory = key.directory();
      if (index >= 0) {
        var stack = directory == null ? manager.getResourceStack(id) : stacks(directory).get(id);
        if (stack != null && index < stack.size()) {
          return stack.get(index);
        }
      } else {
        var resource = directory == null ? manager.getResource(id).orElse(null) : listing(directory).get(id);
        if (resource != null) {
          return resource;
        }
      }
      return null;
    }

  }

  /**
   * Hashes the native source IDs in priority order.
   *
   * @param digest digest to update.
   * @param resources resolved native stack.
   */
  static void appendSources(MessageDigest digest, List<Resource> resources) {
    resources.forEach(resource -> text(digest, resource.sourcePackId()));
  }

  /**
   * Hashes the identifiers and native source IDs of a listing in identifier order.
   *
   * @param digest digest to update.
   * @param resources winning resource per listed identifier.
   */
  static void appendList(MessageDigest digest, Map<Identifier, Resource> resources) {
    new TreeMap<>(resources).forEach((id, resource) -> {
      text(digest, id.toString());
      text(digest, resource.sourcePackId());
    });
  }

  /**
   * Hashes the identifiers and ordered source stacks of a listing.
   *
   * @param digest digest to update.
   * @param resources native stack per listed identifier.
   */
  static void appendStacks(MessageDigest digest, Map<Identifier, List<Resource>> resources) {
    new TreeMap<>(resources).forEach((id, stack) -> {
      text(digest, id.toString());
      text(digest, Integer.toString(stack.size()));
      appendSources(digest, stack);
    });
  }

  /**
   * Hashes the effective JSON sidecar or its absence.
   *
   * @param metadata associated resource metadata.
   * @return hexadecimal metadata fingerprint.
   * @throws IOException if metadata cannot be represented as stable file JSON.
   */
  private static String metadataFingerprint(ResourceMetadata metadata) throws IOException {
    var digest = digest();
    if (metadata instanceof ResourceMetadataJson raw) {
      text(digest, "json");
      text(digest, raw.json().toString());
    } else if (metadata == ResourceMetadata.EMPTY) {
      text(digest, "absent");
    } else {
      throw new IOException("Resource metadata has no stable file representation");
    }
    return hex(digest);
  }

  /**
   * Hashes the source ID and immutable content fingerprint of a resolved resource.
   *
   * @param digest digest to update.
   * @param resource resolved native resource.
   * @param input stream over the resolved file's bytes.
   * @throws IOException if the underlying I/O operation fails.
   */
  private static void appendContent(MessageDigest digest, Resource resource, InputStream input) throws IOException {
    text(digest, resource.sourcePackId());
    if (input instanceof PackFile.CapturedStream snapshot) {
      text(digest, snapshot.digest());
    } else {
      var content = digest();
      input.transferTo(new DigestOutputStream(OutputStream.nullOutputStream(), content));
      text(digest, hex(content));
    }
  }

  /**
   * Creates a new SHA-256 digest.
   *
   * @return digest for one cache proof.
   */
  static MessageDigest digest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException failure) {
      throw new IllegalStateException(failure);
    }
  }

  /**
   * Hashes one UTF-8 string with a length prefix.
   *
   * @param digest digest to update.
   * @param value string to hash.
   */
  static void text(MessageDigest digest, String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    digest.update(java.nio.ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
    digest.update(bytes);
  }

  /**
   * Finishes a digest and encodes it as lowercase hexadecimal.
   *
   * @param digest message digest to update.
   * @return hexadecimal digest.
   */
  static String hex(MessageDigest digest) {
    return HexFormat.of().formatHex(digest.digest());
  }

  /**
   * Identifies an observable native query or file read.
   */
  enum Kind {
    /**
     * Winning source of a direct resource lookup.
     */
    RESOURCE,
    /**
     * Ordered sources of a direct resource stack lookup.
     */
    STACK,
    /**
     * Winning sources of a directory listing.
     */
    LIST,
    /**
     * Ordered sources of the resource stacks in a directory listing.
     */
    LIST_STACKS,
    /**
     * Native namespaces visible to the callback.
     */
    NAMESPACES,
    /**
     * Captured pack selection and order.
     */
    PACKS,
    /**
     * Enabled native feature flags.
     */
    FEATURES,
    /**
     * Effective sidecar contents or absence.
     */
    METADATA,
    /**
     * Resolved file source and bytes.
     */
    CONTENT
  }

  /**
   * Identifies the lookup that resolved a file and the limit used to read it.
   *
   * @param id resolved file identifier.
   * @param directory listing directory, or {@code null} for a direct lookup.
   * @param stackIndex position in the stack, or {@code -1} for a winning resource.
   * @param maxBytes maximum size allowed for the original read.
   */
  record FileAddress(Identifier id, @Nullable String directory, int stackIndex, long maxBytes) {
    /**
     * @param id resolved file identifier.
     * @param directory listing directory, or {@code null} for a direct lookup.
     * @param stackIndex position in the stack, or {@code -1} for a winning resource.
     * @param maxBytes maximum size allowed for the original read.
     */
    FileAddress {
      Objects.requireNonNull(id);
      if (stackIndex < -1 || maxBytes < 0) {
        throw new IllegalArgumentException("Invalid resource stack index");
      }
    }
  }

  /**
   * Describes a native observation for later verification.
   *
   * @param kind kind of observed query.
   * @param key query argument, or empty for a file observation.
   * @param file address for a file observation, or {@code null} otherwise.
   */
  record Request(Kind kind, String key, @Nullable FileAddress file) {
    /**
     * @param kind kind of observed query.
     * @param key query argument, or empty for a file observation.
     * @param file address for a file observation, or {@code null} otherwise.
     */
    Request {
      Objects.requireNonNull(kind);
      Objects.requireNonNull(key);
      if ((kind == Kind.CONTENT || kind == Kind.METADATA) != (file != null)) {
        throw new IllegalArgumentException("Resource observations require a file address");
      }
    }

    /**
     * @param kind kind of observed query.
     * @param key query argument.
     */
    Request(Kind kind, String key) { this(kind, key, null); }

    /**
     * @param kind kind of file observation.
     * @param file address of the resolved file.
     */
    Request(Kind kind, FileAddress file) { this(kind, "", file); }
  }
}
