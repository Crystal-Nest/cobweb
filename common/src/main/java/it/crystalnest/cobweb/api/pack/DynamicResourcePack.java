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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
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
    this.metadata = Suppliers.memoize(() -> new PackMetadataSection(Component.translatable(namespace + "_dynamic_" + name.getPath()), SharedConstants.getCurrentVersion().getPackVersion(type.bridgeType)));
  }

  /**
   * Register the {@link Pack} instance of this dynamic resource pack.
   */
  public void register() {
    Services.REGISTRY.registerDynamicResourcePack(
      type,
      Suppliers.memoize(() -> {
        this.build();
        return new Pack(
          getName(),
          Component.translatable(getName()),
          true,
          () -> this,
          this.metadata.get(),
          type,
          Pack.Position.TOP,
          PackSource.BUILT_IN
        );
      })
    );
  }

  @Override
  public String toString() {
    return getName();
  }

  @Override
  @Nullable
  public InputStream getRootResource(@NotNull String fileName) {
    return null;
  }

  @NotNull
  @Override
  public InputStream getResource(@NotNull PackType type, @NotNull ResourceLocation id) {
    if (this.type == type && this.resources.containsKey(id)) {
      return new ByteArrayInputStream(this.resources.get(id).get());
    }
    return new ByteArrayInputStream(new byte[0]);
  }

  @Override
  public boolean hasResource(@NotNull PackType type, @NotNull ResourceLocation id) {
    return this.type == type && this.resources.containsKey(id);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T getMetadataSection(MetadataSectionSerializer<T> serializer) {
    return serializer.getMetadataSectionName().equals(PackMetadataSection.SERIALIZER.getMetadataSectionName()) ? (T) this.metadata : null;
  }

  @NotNull
  @Override
  public Set<String> getNamespaces(@NotNull PackType packType) {
    return this.namespaces;
  }

  @NotNull
  @Override
  public String getName() {
    return name.toString();
  }

  @Override
  public void close() {}

  @NotNull
  @Override
  public Collection<ResourceLocation> getResources(@NotNull PackType type, @NotNull String namespace, @NotNull String id, @NotNull Predicate<ResourceLocation> allowedPathPredicate) {
    if (this.type == type && this.namespaces.contains(namespace)) {
      return this.resources.keySet().stream().filter(resource -> resource.getNamespace().equals(namespace) && resource.getPath().startsWith(id) && allowedPathPredicate.test(resource)).toList();
    }
    return Collections.emptyList();
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
}
