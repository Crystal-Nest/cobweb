package it.crystalnest.cobweb.api.pack;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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

  GenerationResources(GenerationContext context, ResourceManager manager) {
    this.context = context;
    this.manager = manager;
  }

  /**
   * Finds the winning resource for an exact path.
   *
   * @param id namespaced file path.
   * @return winning visible resource, if any.
   */
  public Optional<File> get(Identifier id) {
    context.checkOpen();
    context.observe(CacheObservations.Kind.RESOURCE, id.toString());
    return manager.getResource(id).map(resource -> new File(resource));
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
    context.observe(CacheObservations.Kind.STACK, id.toString());
    return manager.getResourceStack(id).stream().map(resource -> new File(resource)).toList();
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
    context.observe(CacheObservations.Kind.LIST, directory);
    return manager.listResources(directory, selector).entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> new File(entry.getValue())));
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
    context.observe(CacheObservations.Kind.LIST_STACKS, directory);
    return manager.listResourceStacks(directory, selector).entrySet().stream()
      .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> entry.getValue().stream().map(File::new).toList()));
  }

  /**
   * Returns the namespaces routed by the resource manager.
   *
   * @return immutable set of visible namespaces.
   */
  public Set<String> namespaces() {
    context.checkOpen();
    context.observe(CacheObservations.Kind.NAMESPACES, "");
    return Collections.unmodifiableSet(new LinkedHashSet<>(manager.getNamespaces()));
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

    private File(Resource resource) {
      this.resource = resource;
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
     * Opens a fresh stream scoped to the callback. Cobweb closes it at callback completion.
     *
     * @return stream of the resource contents.
     * @throws IOException if the file cannot be opened.
     */
    public InputStream open() throws IOException {
      context.checkOpen();
      return context.stream(resource.open());
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
      context.untrackedRead();
      return resource.metadata().getSection(type);
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
