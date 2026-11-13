package it.crystalnest.cobweb.api.pack.generation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import it.crystalnest.cobweb.api.pack.generation.output.ResourceKind;
import it.crystalnest.cobweb.impl.pack.ResourceMetadataJson;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceMetadata;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Native resource resolution against one stable generation snapshot.
 */
public final class GenerationResources {
  private final GenerationContext context;

  private final ResourceManager manager;

  private final CacheObservations observations;

  GenerationResources(GenerationContext context, ResourceManager manager, CacheObservations observations) {
    this.context = context;
    this.manager = manager;
    this.observations = observations;
  }

  /**
   * Finds the winning resource for an exact path.
   *
   * @param id namespaced file path.
   * @return winning visible resource, if any.
   */
  public Optional<File> get(Identifier id) {
    context.checkOpen();
    var resource = manager.getResource(id);
    observations.observe(CacheObservations.Kind.RESOURCE, id.toString(), digest -> resource.ifPresent(file -> CacheObservations.text(digest, file.sourcePackId())));
    return resource.map(file -> new File(file, id, null, -1));
  }

  /**
   * Reads the winning complete definition of a reusable resource family.
   *
   * @param kind reusable resource contract.
   * @param id logical resource identifier.
   * @param <T> definition type.
   * @return detached definition if present.
   * @throws IOException if the winning file cannot be read.
   */
  public <T> Optional<T> get(ResourceKind<T> kind, Identifier id) throws IOException {
    context.checkOpen();
    if (kind.deadline().type() != context.phase().type()) {
      throw new IllegalArgumentException("Resource kind " + kind.directory() + " is unavailable in " + context.phase());
    }
    var file = get(kind.file(id));
    return file.isEmpty() ? Optional.empty() : Optional.of(file.get().decode(kind.codec()));
  }

  /**
   * Returns the native resource stack in increasing pack priority.
   *
   * @param id namespaced file path.
   * @return visible versions of the file.
   */
  public List<File> stack(Identifier id) {
    context.checkOpen();
    var stack = manager.getResourceStack(id);
    observations.observe(CacheObservations.Kind.STACK, id.toString(), digest -> CacheObservations.appendSources(digest, stack));
    return stackFiles(id, null, stack);
  }

  /**
   * Lists winning resources below a native resource directory.
   *
   * @param directory relative directory.
   * @param selector native identifier selector.
   * @return immutable map of winning resources.
   */
  public Map<Identifier, File> list(String directory, ResourceManager.Selector selector) {
    context.checkOpen();
    boolean tracking = observations.tracking();
    var files = manager.listResources(directory, tracking ? id -> true : selector);
    if (tracking) {
      observations.observe(CacheObservations.Kind.LIST, directory, digest -> CacheObservations.appendList(digest, files));
    }
    return files.entrySet().stream().filter(entry -> !tracking || selector.isIncluded(entry.getKey()))
      .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> new File(entry.getValue(), entry.getKey(), directory, -1)));
  }

  /**
   * Lists native resource stacks below a directory in increasing pack priority.
   *
   * @param directory relative directory.
   * @param selector native identifier selector.
   * @return immutable map of visible resource stacks.
   */
  public Map<Identifier, List<File>> listStacks(String directory, ResourceManager.Selector selector) {
    context.checkOpen();
    boolean tracking = observations.tracking();
    var files = manager.listResourceStacks(directory, tracking ? id -> true : selector);
    if (tracking) {
      observations.observe(CacheObservations.Kind.LIST_STACKS, directory, digest -> CacheObservations.appendStacks(digest, files));
    }
    return files.entrySet().stream().filter(entry -> !tracking || selector.isIncluded(entry.getKey()))
      .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> stackFiles(entry.getKey(), directory, entry.getValue())));
  }

  private List<File> stackFiles(Identifier id, @Nullable String directory, List<Resource> stack) {
    var files = new ArrayList<File>(stack.size());
    for (int index = 0; index < stack.size(); index++) {
      files.add(new File(stack.get(index), id, directory, index));
    }
    return List.copyOf(files);
  }

  /**
   * Returns the namespaces routed by the resource manager.
   *
   * @return immutable set of visible namespaces.
   */
  public Set<String> namespaces() {
    context.checkOpen();
    var namespaces = new LinkedHashSet<>(manager.getNamespaces());
    observations.observe(CacheObservations.Kind.NAMESPACES, "", digest -> namespaces.stream().sorted().forEach(namespace -> CacheObservations.text(digest, namespace)));
    return Collections.unmodifiableSet(namespaces);
  }

  /**
   * Reads the translations contributed by the visible stack of one language file.
   *
   * @param namespace resource namespace.
   * @param language language identifier such as {@code en_us}.
   * @return immutable translations merged in native pack order.
   * @throws IOException if a translation file cannot be read.
   */
  public Map<String, String> translations(String namespace, String language) throws IOException {
    context.checkAssets();
    Map<String, String> translations = new LinkedHashMap<>();
    for (var file : stack(Identifier.fromNamespaceAndPath(namespace, "lang/" + language + ".json"))) {
      try (var input = file.open()) {
        Language.loadFromJson(input, translations::put);
      }
    }
    return Map.copyOf(translations);
  }

  /**
   * Read-only handle to a resource and its native source.
   */
  public final class File {
    private final Resource resource;

    private final Identifier id;

    @Nullable
    private final String directory;

    private final int stackIndex;

    private File(Resource resource, Identifier id, @Nullable String directory, int stackIndex) {
      this.resource = resource;
      this.id = id;
      this.directory = directory;
      this.stackIndex = stackIndex;
    }

    /**
     * Returns the native source pack ID.
     *
     * @return native pack ID.
     */
    public String packId() {
      context.checkOpen();
      return resource.sourcePackId();
    }

    /**
     * Opens a fresh stream scoped to the callback. Readers of the same resolved file in this stage share its first captured contents. Cobweb closes the stream at callback completion.
     *
     * @return stream of the resource contents.
     * @throws IOException if the file cannot be opened.
     */
    public InputStream open() throws IOException {
      return open(Long.MAX_VALUE);
    }

    /**
     * Opens a resource with a capture limit. An oversized first capture fails this stage's shared input.
     *
     * @param maxBytes maximum captured bytes.
     * @return callback-scoped stream.
     * @throws IOException if the resource exceeds the limit or cannot be opened.
     * @throws IllegalArgumentException if the limit is negative.
     */
    public InputStream open(long maxBytes) throws IOException {
      if (maxBytes < 0) {
        throw new IllegalArgumentException("Resource size limit must be nonnegative");
      }
      context.checkOpen();
      try {
        var input = PackIO.open(resource, maxBytes);
        try {
          PackIO.checkLimit(input, maxBytes);
          observations.observeContent(id, directory, stackIndex, maxBytes, resource, input);
          return context.stream(input);
        } catch (IOException | RuntimeException | Error failure) {
          try {
            input.close();
          } catch (IOException cleanup) {
            failure.addSuppressed(cleanup);
          }
          throw failure;
        }
      } catch (IOException | RuntimeException failure) {
        observations.unsupported();
        throw failure;
      }
    }

    /**
     * Reads a native metadata section associated with this exact resource resolution.
     *
     * @param type native metadata section.
     * @param <T> decoded section type.
     * @return decoded metadata when present.
     * @throws IOException if the metadata cannot be read.
     */
    public <T> Optional<T> metadata(MetadataSectionType<T> type) throws IOException {
      return observedMetadata().getSection(type);
    }

    /**
     * Reads a detached copy of the native sidecar associated with this resource.
     *
     * @return original JSON sidecar when available.
     * @throws IOException if the sidecar cannot be read.
     */
    public Optional<JsonObject> metadataJson() throws IOException {
      return observedMetadata() instanceof ResourceMetadataJson raw ? Optional.of(raw.json().deepCopy()) : Optional.empty();
    }

    private ResourceMetadata observedMetadata() throws IOException {
      context.checkOpen();
      try {
        var metadata = resource.metadata();
        observations.observeMetadata(id, directory, stackIndex, metadata);
        return metadata;
      } catch (IOException | RuntimeException failure) {
        context.untrackedRead();
        throw failure;
      }
    }

    /**
     * Decodes this file as a detached value using the native registry context.
     *
     * @param codec resource codec.
     * @param <T> decoded type.
     * @return decoded resource.
     * @throws IOException if the file cannot be read.
     */
    public <T> T decode(Codec<T> codec) throws IOException {
      try (var reader = new InputStreamReader(open(), StandardCharsets.UTF_8)) {
        return codec.parse(context.jsonOps(), JsonParser.parseReader(reader)).getOrThrow();
      }
    }
  }
}
