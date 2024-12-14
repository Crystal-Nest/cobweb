package it.crystalnest.cobweb.api.pack.dynamic;

import com.google.common.base.Suppliers;
import com.google.gson.JsonElement;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonWriter;
import it.crystalnest.cobweb.Constants;
import it.crystalnest.cobweb.platform.Services;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
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
import java.util.Optional;
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

  private final PackLocationInfo location;

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
    this.location = new PackLocationInfo(name.toString(), Component.translatable(name.toString()), PackSource.BUILT_IN, Optional.empty());
    this.type = type;
    this.name = name;
    this.namespace = name.getNamespace();
    this.namespaces.add(namespace);
    this.metadata = Suppliers.memoize(() -> new PackMetadataSection(Component.translatable(namespace + "_dynamic_" + name.getPath()), SharedConstants.getCurrentVersion().getPackVersion(type), Optional.empty()));
  }

  /**
   * Register the {@link Pack} instance of this dynamic resource pack.
   */
  public void register() {
    Services.REGISTRY.registerDynamicResourcePack(
      type,
      Suppliers.memoize(() -> {
        this.build();
        return new Pack(location(), new DynamicResourcesSupplier(this), Services.PLATFORM.createPackMetadata(metadata.get().description()), new PackSelectionConfig(true, Pack.Position.TOP, false));
      })
    );
  }

  @Override
  public String toString() {
    return packId();
  }

  @NotNull
  @Override
  public PackLocationInfo location() {
    return location;
  }

  @Nullable
  @Override
  public IoSupplier<InputStream> getRootResource(String @NotNull ... strings) {
    return null;
  }

  @Override
  public IoSupplier<InputStream> getResource(@NotNull PackType type, @NotNull ResourceLocation id) {
    if (resources.containsKey(id)) {
      return () -> {
        if (this.type == type) {
          return new ByteArrayInputStream(resources.get(id).get());
        }
        throw new IOException(String.format("Tried to access wrong type of resource on %s.", name));
      };
    }
    return null;
  }

  @Override
  public void listResources(@NotNull PackType type, @NotNull String namespace, @NotNull String id, @NotNull ResourceOutput output) {
    if (this.type == type && namespaces.contains(namespace)) {
      this.resources.entrySet().stream()
        .filter(resource -> (resource.getKey().getNamespace().equals(namespace) && resource.getKey().getPath().startsWith(id)))
        .forEach(resource -> output.accept(resource.getKey(), () -> new ByteArrayInputStream(resource.getValue().get())));
    }
  }

  @NotNull
  @Override
  public Set<String> getNamespaces(@NotNull PackType packType) {
    return namespaces;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T getMetadataSection(MetadataSectionSerializer<T> serializer) {
    return serializer.getMetadataSectionName().equals(PackMetadataSection.TYPE.getMetadataSectionName()) ? (T) metadata : null;
  }

  @Override
  public void close() {}

  /**
   * Builds the provided bytes to the given path.
   *
   * @param path data location.
   * @param bytes raw data.
   */
  private void build(ResourceLocation path, Supplier<byte[]> bytes) {
    namespaces.add(path.getNamespace());
    resources.put(path, Suppliers.memoize(bytes::get));
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
      build(DynamicResourceType.GENERAL.getPath(path), () -> {
        try (StringWriter stringWriter = new StringWriter(); JsonWriter jsonWriter = new JsonWriter(stringWriter)) {
          jsonWriter.setIndent("  ");
          Streams.write(element, jsonWriter);
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
    public PackResources openPrimary(@NotNull PackLocationInfo name) {
      return instance;
    }

    @NotNull
    @Override
    public PackResources openFull(@NotNull PackLocationInfo name, @NotNull Pack.Metadata info) {
      return instance;
    }
  }
}
