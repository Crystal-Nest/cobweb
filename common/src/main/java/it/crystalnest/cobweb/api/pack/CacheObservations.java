package it.crystalnest.cobweb.api.pack;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.flag.FeatureFlagSet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

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
        entries.computeIfAbsent(request, key -> {
          try {
            return fingerprint(key);
          } catch (IOException failure) {
            supported = false;
            return "";
          }
        });
      } catch (RuntimeException failure) {
        supported = false;
      }
    }
  }

  void unsupported() {
    supported = false;
  }

  Map<Request, String> entries() {
    return Map.copyOf(entries);
  }

  boolean validate() {
    return supported && matches(entries);
  }

  boolean matches(Map<Request, String> previous) {
    try {
      for (var entry : previous.entrySet()) {
        if (!entry.getValue().equals(fingerprint(entry.getKey()))) {
          return false;
        }
      }
      return true;
    } catch (IOException | RuntimeException failure) {
      return false;
    }
  }

  String fingerprint(Request request) throws IOException {
    MessageDigest digest = digest();
    switch (request.kind()) {
      case RESOURCE -> {
        var resource = manager.getResource(Identifier.parse(request.key()));
        if (resource.isPresent()) {
          append(digest, resource.get());
        }
      }
      case STACK -> {
        for (Resource resource : manager.getResourceStack(Identifier.parse(request.key()))) {
          append(digest, resource);
        }
      }
      case LIST -> {
        var files = new TreeMap<>(manager.listResources(request.key(), id -> true));
        for (var entry : files.entrySet()) {
          text(digest, entry.getKey().toString());
          append(digest, entry.getValue());
        }
      }
      case LIST_STACKS -> {
        var files = new TreeMap<>(manager.listResourceStacks(request.key(), id -> true));
        for (var entry : files.entrySet()) {
          text(digest, entry.getKey().toString());
          text(digest, Integer.toString(entry.getValue().size()));
          for (var resource : entry.getValue()) {
            append(digest, resource);
          }
        }
      }
      case NAMESPACES -> manager.getNamespaces().forEach(namespace -> text(digest, namespace));
      case PACKS -> text(digest, packs.toString());
      case FEATURES -> FeatureFlags.REGISTRY.toNames(features).stream().sorted().forEach(flag -> text(digest, flag.toString()));
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  private static void append(MessageDigest digest, Resource resource) throws IOException {
    text(digest, resource.sourcePackId());
    var file = digest();
    try (var stream = resource.open()) {
      byte[] buffer = new byte[8192];
      int read;
      while ((read = stream.read(buffer)) != -1) {
        file.update(buffer, 0, read);
      }
    }
    digest.update(file.digest());
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
    FEATURES
  }

  record Request(Kind kind, String key) {}
}
