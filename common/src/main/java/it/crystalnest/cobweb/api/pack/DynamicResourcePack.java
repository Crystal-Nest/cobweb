package it.crystalnest.cobweb.api.pack;

import com.google.common.base.Suppliers;
import com.google.gson.JsonElement;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonWriter;
import it.crystalnest.cobweb.Constants;
import it.crystalnest.cobweb.platform.Services;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Dynamic resource pack.
 */
public abstract class DynamicResourcePack implements PackResources {
  /**
   * {@link PackType}.
   */
  private final PackType type;

  /**
   * Pack name.
   */
  private final ResourceLocation name;

  /**
   * Pack namespace.
   */
  private final String namespace;

  /**
   * Pack related namespaces.
   */
  private final Set<String> namespaces = new HashSet<>();

  /**
   * Pack metadata.
   */
  private final Supplier<PackMetadataSection> metadata;

  /**
   * Pack resources.
   */
  private final Map<ResourceLocation, Supplier<byte[]>> resources = new ConcurrentHashMap<>();

  /**
   * @param name {@link #name}.
   * @param type {@link #type}.
   */
  protected DynamicResourcePack(ResourceLocation name, PackType type) {
    this.type = type;
    this.name = name;
    this.namespace = name.getNamespace();
    this.namespaces.add(namespace);
    this.metadata = Suppliers.memoize(() -> new PackMetadataSection(Component.translatable(namespace + "_dynamic_" + name.getPath()), SharedConstants.getCurrentVersion().getPackVersion(type)));
  }

  /**
   * Register the {@link Pack} instance of this dynamic resource pack.
   */
  public void register() {
    Services.REGISTRY.registerDynamicResourcePack(
      type,
      Suppliers.memoize(() -> {
        this.build();
        return Pack.create(
          packId(),
          Component.translatable(packId()),
          true,
          new DynamicResourcesSupplier(this),
          Services.PLATFORM.createPackInfo(metadata.get().getDescription(), metadata.get().getPackFormat()),
          type,
          Pack.Position.TOP,
          false,
          PackSource.BUILT_IN
        );
      })
    );
  }

  @Override
  public String toString() {
    return packId();
  }

  @Nullable
  @Override
  public IoSupplier<InputStream> getRootResource(String @NotNull ... strings) {
    return null;
  }

  @Override
  public IoSupplier<InputStream> getResource(@NotNull PackType type, @NotNull ResourceLocation id) {
    if (this.resources.containsKey(id)) {
      return () -> {
        if (this.type == type) {
          return new ByteArrayInputStream(this.resources.get(id).get());
        }
        throw new IOException(String.format("Tried to access wrong type of resource on %s.", this.name));
      };
    }
    return null;
  }

  @NotNull
  @Override
  public Set<String> getNamespaces(@NotNull PackType packType) {
    return this.namespaces;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T getMetadataSection(MetadataSectionSerializer<T> serializer) {
    return serializer.getMetadataSectionName().equals(PackMetadataSection.TYPE.getMetadataSectionName()) ? (T) this.metadata : null;
  }

  @Override
  public void close() {}

  @Override
  public void listResources(@NotNull PackType type, @NotNull String namespace, @NotNull String id, @NotNull ResourceOutput output) {
    if (this.type == type && this.namespaces.contains(namespace)) {
      this.resources.entrySet().stream()
        .filter(resource -> (resource.getKey().getNamespace().equals(namespace) && resource.getKey().getPath().startsWith(id)))
        .forEach(resource -> output.accept(resource.getKey(), () -> new ByteArrayInputStream(resource.getValue().get())));
    }
  }

  @NotNull
  @Override
  public String packId() {
    return name.toString();
  }

  /**
   * Builds the provided bytes to the given path.
   *
   * @param path data location.
   * @param bytes raw data.
   */
  private void build(ResourceLocation path, Supplier<byte[]> bytes) {
    this.namespaces.add(path.getNamespace());
    this.resources.put(path, Suppliers.memoize(bytes::get));
  }

  /**
   * Builds the provided {@link JsonElement}s to the given paths.
   *
   * @param paths data locations.
   * @param json {@link JsonElement}s.
   */
  protected void build(List<ResourceLocation> paths, Supplier<JsonElement> json) {
    for (ResourceLocation path : paths) {
      JsonElement element = json.get();
      this.build(DynamicResourceType.GENERAL.getPath(path), () -> {
        try (StringWriter stringWriter = new StringWriter(); JsonWriter jsonWriter = new JsonWriter(stringWriter)) {
          jsonWriter.setIndent("  ");
          Streams.write(element, jsonWriter);
          jsonWriter.close();
          return stringWriter.toString().getBytes();
        } catch (IOException e) {
          Constants.LOGGER.error("Failed to write JSON {} to resource pack\n{}.", path, name, e);
          return new byte[0];
        }
      });
    }
  }

  /**
   * Starts the building process.
   */
  protected abstract void build();

  /**
   * Dynamic {@link Pack.ResourcesSupplier}.
   */
  public static class DynamicResourcesSupplier implements Pack.ResourcesSupplier {
    /**
     * {@link PackResources} instance.
     */
    private final PackResources instance;

    /**
     * @param instance {@link #instance}.
     */
    DynamicResourcesSupplier(PackResources instance) {
      this.instance = instance;
    }

    @NotNull
    @Override
    public PackResources open(@NotNull String name) {
      return this.instance;
    }
  }
}
