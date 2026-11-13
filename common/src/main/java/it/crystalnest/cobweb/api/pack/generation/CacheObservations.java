package it.crystalnest.cobweb.api.pack.generation;

import it.crystalnest.cobweb.api.pack.query.PackQueries;
import it.crystalnest.cobweb.impl.pack.ResourceMetadataJson;
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
  private final ResourceManager manager;

  private final PackQueries packs;

  private final FeatureFlagSet features;

  private final Map<Request, String> entries = new ConcurrentHashMap<>();

  private volatile boolean supported = true;

  CacheObservations(ResourceManager manager, PackQueries packs, FeatureFlagSet features, boolean enabled) {
    this.manager = manager;
    this.packs = packs;
    this.features = features;
    supported = enabled;
  }

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

  private void record(Request request, String fingerprint) {
    var previous = entries.putIfAbsent(request, fingerprint);
    if (previous != null && !previous.equals(fingerprint)) {
      supported = false;
    }
  }

  void unsupported() {
    supported = false;
  }

  boolean tracking() {
    return supported;
  }

  Map<Request, String> entries() {
    return Map.copyOf(entries);
  }

  boolean validate() {
    return supported && matches(entries);
  }

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

  String fingerprint(Request request) throws IOException {
    return new Verification().fingerprint(request);
  }

  Verification verification() { return new Verification(); }

  final class Verification {
    private final Map<String, Map<Identifier, Resource>> listings = new HashMap<>();

    private final Map<String, Map<Identifier, List<Resource>>> stackListings = new HashMap<>();

    private Map<Identifier, Resource> listing(String directory) {
      return listings.computeIfAbsent(directory, path -> manager.listResources(path, id -> true));
    }

    private Map<Identifier, List<Resource>> stacks(String directory) {
      return stackListings.computeIfAbsent(directory, path -> manager.listResourceStacks(path, id -> true));
    }

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

  static void appendSources(MessageDigest digest, List<Resource> resources) {
    resources.forEach(resource -> text(digest, resource.sourcePackId()));
  }

  static void appendList(MessageDigest digest, Map<Identifier, Resource> resources) {
    new TreeMap<>(resources).forEach((id, resource) -> {
      text(digest, id.toString());
      text(digest, resource.sourcePackId());
    });
  }

  static void appendStacks(MessageDigest digest, Map<Identifier, List<Resource>> resources) {
    new TreeMap<>(resources).forEach((id, stack) -> {
      text(digest, id.toString());
      text(digest, Integer.toString(stack.size()));
      appendSources(digest, stack);
    });
  }

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

  static MessageDigest digest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException failure) {
      throw new IllegalStateException(failure);
    }
  }

  static void text(MessageDigest digest, String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    digest.update(java.nio.ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
    digest.update(bytes);
  }

  static String hex(MessageDigest digest) {
    return HexFormat.of().formatHex(digest.digest());
  }

  enum Kind {
    RESOURCE,
    STACK,
    LIST,
    LIST_STACKS,
    NAMESPACES,
    PACKS,
    FEATURES,
    METADATA,
    CONTENT
  }

  record FileAddress(Identifier id, @Nullable String directory, int stackIndex, long maxBytes) {
    FileAddress {
      Objects.requireNonNull(id);
      if (stackIndex < -1 || maxBytes < 0) {
        throw new IllegalArgumentException("Invalid resource stack index");
      }
    }
  }

  record Request(Kind kind, String key, @Nullable FileAddress file) {
    Request {
      Objects.requireNonNull(kind);
      Objects.requireNonNull(key);
      if ((kind == Kind.CONTENT || kind == Kind.METADATA) != (file != null)) {
        throw new IllegalArgumentException("Resource observations require a file address");
      }
    }

    Request(Kind kind, String key) { this(kind, key, null); }

    Request(Kind kind, FileAddress file) { this(kind, "", file); }
  }
}
