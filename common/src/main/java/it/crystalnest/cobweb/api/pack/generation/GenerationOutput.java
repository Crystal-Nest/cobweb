package it.crystalnest.cobweb.api.pack.generation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import it.crystalnest.cobweb.api.pack.generation.output.ResourceKind;
import it.crystalnest.cobweb.api.pack.generation.output.SoundDefinition;
import it.crystalnest.cobweb.api.registry.CobwebDataPackRegistry;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagFile;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Receives complete resource contributions for one callback.
 */
public final class GenerationOutput {
  private final GenerationContext context;

  private final Consumer<PackContribution> submit;

  GenerationOutput(GenerationContext context, Consumer<PackContribution> submit) {
    this.context = context;
    this.submit = submit;
  }

  /**
   * Submits a complete binary file and copies the caller's bytes.
   *
   * @param id namespaced path, including the extension.
   * @param bytes resource contents.
   */
  public void putBytes(Identifier id, byte[] bytes) {
    try {
      write(id, stream -> stream.write(bytes));
    } catch (IOException failure) {
      throw new UncheckedIOException("Unable to submit " + id, failure);
    }
  }

  /**
   * Submits a complete UTF-8 file.
   *
   * @param id namespaced path, including the extension.
   * @param value file contents.
   */
  public void putText(Identifier id, String value) {
    putBytes(id, value.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Submits a complete JSON file.
   *
   * @param id namespaced path, including the extension.
   * @param value JSON value.
   */
  public void putJson(Identifier id, JsonElement value) {
    putText(id, value.toString());
  }

  /**
   * Submits a complete client translation file for one namespace and language.
   *
   * @param namespace resource namespace.
   * @param language language identifier such as {@code en_us}.
   * @param translations complete translation mappings.
   */
  public void putTranslations(String namespace, String language, Map<String, String> translations) {
    context.checkAssets();
    var json = new JsonObject();
    translations.forEach(json::addProperty);
    putJson(Identifier.fromNamespaceAndPath(namespace, "lang/" + language + ".json"), json);
  }

  /**
   * Submits an encoded PNG in the client texture directory.
   *
   * @param texture texture identifier without its directory and extension.
   * @param png writer of complete PNG bytes.
   * @throws IOException if the writer fails.
   */
  public void putTexture(Identifier texture, FileWriter png) throws IOException {
    context.checkAssets();
    write(PackPaths.texture(texture), png);
  }

  /**
   * Submits encoded audio in the client sound directory.
   *
   * @param sound sound identifier without its directory and extension.
   * @param ogg writer of complete Ogg Vorbis bytes.
   * @throws IOException if the writer fails.
   */
  public void putSound(Identifier sound, FileWriter ogg) throws IOException {
    context.checkAssets();
    write(PackPaths.sound(sound), ogg);
  }

  /**
   * Submits a complete sound event as one keyed contribution to the namespace's {@code sounds.json}.
   *
   * @param event sound event identifier.
   * @param definition complete sound event definition.
   */
  public void putSoundDefinition(Identifier event, SoundDefinition definition) {
    context.checkAssets();
    var id = event.withPath("sounds.json");
    try {
      submit(PackContribution.member(id, event.getPath(), jsonFile(definition.toJson())));
    } catch (IOException failure) {
      throw new UncheckedIOException("Unable to submit sound event " + event, failure);
    }
  }

  /**
   * Submits an encoded sprite and optional complete sidecar as one contribution.
   * Omitting the sidecar removes only an earlier helper-owned sidecar from this pack.
   *
   * @param texture texture identifier without its directory and extension.
   * @param png writer of complete PNG bytes.
   * @param metadata optional sidecar JSON object.
   * @throws IOException if the writer or metadata encoding fails.
   */
  public void putSprite(Identifier texture, FileWriter png, @Nullable JsonObject metadata) throws IOException {
    context.checkAssets();
    writeWithMetadata(PackPaths.texture(texture), png, metadata);
  }

  /**
   * Submits a compressed native structure file.
   *
   * @param structure structure identifier without its directory and extension.
   * @param value complete structure NBT.
   * @throws IOException if encoding fails.
   */
  public void putStructure(Identifier structure, CompoundTag value) throws IOException {
    context.checkData();
    write(structure.withPath(path -> "structure/" + path + ".nbt"), stream -> NbtIo.writeCompressed(value, stream));
  }

  /**
   * Submits a complete JSON file encoded with the current registry context.
   *
   * @param id namespaced path, including the extension.
   * @param codec entry codec.
   * @param value complete entry.
   * @param <T> entry type.
   */
  public <T> void putEncoded(Identifier id, Codec<T> codec, T value) {
    putJson(id, codec.encodeStart(context.jsonOps(), value).getOrThrow());
  }

  /**
   * Submits a file after a synchronous writer completes successfully.
   *
   * @param id namespaced path, including the extension.
   * @param writer writes the complete file.
   * @throws IOException if the writer fails.
   */
  public void write(Identifier id, FileWriter writer) throws IOException {
    context.checkOpen();
    submit(PackContribution.file(id, PackFile.write(writer)));
  }

  /**
   * Copies the bytes of a resource to this pack.
   *
   * @param id destination namespaced path, including the extension.
   * @param source source resource from the generation snapshot.
   * @throws IOException if the resource cannot be copied.
   */
  public void copy(Identifier id, GenerationResources.File source) throws IOException {
    copy(id, source, false);
  }

  /**
   * Copies the bytes of a resource to this pack with optional effective file-backed metadata.
   * When metadata is requested, the file and its sidecar share an output contribution.
   * An absent source sidecar clears any earlier grouped sidecar at the destination.
   * Bundled and independently submitted metadata remain available under native pack priority.
   * Cached callbacks track the effective sidecar, including its absence.
   *
   * @param id destination namespaced path, including the extension.
   * @param source source resource from the generation snapshot.
   * @param withMetadata whether to copy the effective JSON sidecar to the destination.
   * @throws IOException if the file or its requested metadata cannot be read or written.
   */
  public void copy(Identifier id, GenerationResources.File source, boolean withMetadata) throws IOException {
    copy(id, source, withMetadata, Long.MAX_VALUE);
  }

  void copy(Identifier id, GenerationResources.File source, boolean withMetadata, long maxBytes) throws IOException {
    context.checkOpen();
    FileWriter writer = stream -> {
      try (var input = source.open(maxBytes)) {
        PackIO.copy(input, stream, maxBytes);
      }
    };
    if (withMetadata) {
      writeWithMetadata(id, writer, source.metadataJson().orElse(null));
    } else {
      write(id, writer);
    }
  }

  private void writeWithMetadata(Identifier id, FileWriter writer, @Nullable JsonObject metadata) throws IOException {
    var file = PackFile.write(writer);
    PackFile companion;
    try {
      companion = metadata == null ? null : jsonFile(metadata);
    } catch (IOException | RuntimeException | Error failure) {
      file.release();
      throw failure;
    }
    submit(PackContribution.withMetadata(id, file, companion));
  }

  private static PackFile jsonFile(JsonElement json) throws IOException {
    return PackFile.write(stream -> stream.write(PackIO.json(json)));
  }

  private void submit(PackContribution contribution) {
    try {
      context.checkOpen();
      submit.accept(contribution);
    } catch (RuntimeException | Error failure) {
      contribution.release();
      throw failure;
    }
  }

  /**
   * Selects the writer for the supplied registry definition.
   *
   * @param registry registered definition.
   * @param <T> entry type.
   * @return writer of complete entries.
   */
  public <T> JsonEntries<T> registryEntries(CobwebDataPackRegistry<T> registry) {
    return registryEntries(registry.key(), registry.codec());
  }

  /**
   * Selects a writer for a native data pack registry.
   *
   * @param key native registry key.
   * @param codec codec for complete entries.
   * @param <T> entry type.
   * @return writer of complete entries.
   */
  public <T> JsonEntries<T> registryEntries(ResourceKey<? extends Registry<T>> key, Codec<T> codec) {
    context.checkData();
    return new JsonEntries<>(FileToIdConverter.registry(key), Objects.requireNonNull(codec));
  }

  /**
   * Selects a writer for a resource family encoded using a JSON codec.
   *
   * @param directory native resource directory, such as {@code models} or {@code blockstates}.
   * @param codec codec for complete resource definitions.
   * @param <T> definition type.
   * @return writer of complete definitions for this directory.
   */
  public <T> JsonEntries<T> jsonEntries(String directory, Codec<T> codec) {
    context.checkOpen();
    return new JsonEntries<>(FileToIdConverter.json(directory), Objects.requireNonNull(codec));
  }

  /**
   * Writes a complete custom JSON resource using its declared path and codec.
   *
   * @param kind reusable resource contract.
   * @param id logical resource identifier.
   * @param value complete definition.
   * @param <T> definition type.
   */
  public <T> void put(ResourceKind<T> kind, Identifier id, T value) {
    context.checkOpen();
    if (kind.deadline().type() != context.phase().type() || context.phase().ordinal() > kind.deadline().ordinal()) {
      throw new IllegalArgumentException("Resource kind " + kind.directory() + " cannot be written in " + context.phase());
    }
    putEncoded(kind.file(id), kind.codec(), value);
  }

  /**
   * Selects the native recipe writer.
   *
   * @return writer for complete recipes.
   */
  public JsonEntries<Recipe<?>> recipes() {
    return registryEntries(Registries.RECIPE, Recipe.DIRECT_CODEC);
  }

  /**
   * Selects the native enchantment writer.
   *
   * @return writer for complete enchantments.
   */
  public JsonEntries<Enchantment> enchantments() {
    return registryEntries(Registries.ENCHANTMENT, Enchantment.DIRECT_CODEC);
  }

  /**
   * Selects the native loot table writer.
   *
   * @return writer for complete loot tables.
   */
  public JsonEntries<LootTable> lootTables() {
    return registryEntries(Registries.LOOT_TABLE, LootTable.DIRECT_CODEC);
  }

  /**
   * Selects the native advancement writer.
   *
   * @return writer for complete advancements.
   */
  public JsonEntries<Advancement> advancements() {
    return registryEntries(Registries.ADVANCEMENT, Advancement.CODEC);
  }

  /**
   * Selects the tag writer for a native registry.
   *
   * @param registry native registry key.
   * @return writer using the native tag directory for the registry.
   */
  public Tags tags(ResourceKey<? extends Registry<?>> registry) {
    context.checkData();
    return new Tags(FileToIdConverter.json(Registries.tagsDirPath(registry)));
  }

  /**
   * Submits complete tags and explicit additions to this pack's tag files.
   */
  public final class Tags {
    private final FileToIdConverter paths;

    private Tags(FileToIdConverter paths) {
      this.paths = paths;
    }

    /**
     * Replaces this pack's own contribution to a tag.
     *
     * @param id tag identifier.
     * @param entries complete native tag entries.
     * @param replace whether Minecraft should discard lower-priority contributions.
     */
    public void put(Identifier id, List<TagEntry> entries, boolean replace) {
      putJson(paths.idToFile(id), TagFile.CODEC.encodeStart(JsonOps.INSTANCE, new TagFile(List.copyOf(entries), replace)).getOrThrow());
    }

    /**
     * Adds members to this pack's tag contribution, retaining its native replacement flag.
     *
     * @param id tag identifier.
     * @param entries members to add.
     */
    public void add(Identifier id, TagEntry... entries) {
      context.checkOpen();
      submit(PackContribution.tag(paths.idToFile(id), new TagAddition(List.of(entries), null)));
    }

    /**
     * Adds members and sets the native replacement flag explicitly.
     *
     * @param id tag identifier.
     * @param replace whether Minecraft should discard lower-priority contributions.
     * @param entries members to add.
     */
    public void add(Identifier id, boolean replace, TagEntry... entries) {
      context.checkOpen();
      submit(PackContribution.tag(paths.idToFile(id), new TagAddition(List.of(entries), replace)));
    }
  }

  record TagAddition(List<TagEntry> entries, @Nullable Boolean replace) {}

  /**
   * Produces a complete file using a Cobweb-owned stream.
   */
  @FunctionalInterface
  public interface FileWriter {
    /**
     * Writes file contents synchronously.
     *
     * @param stream output stream.
     * @throws IOException if writing fails.
     */
    void write(OutputStream stream) throws IOException;
  }

  /**
   * Writes complete definitions for one native JSON resource family.
   *
   * @param <T> entry type.
   */
  public final class JsonEntries<T> {
    private final FileToIdConverter paths;

    private final Codec<T> codec;

    private JsonEntries(FileToIdConverter paths, Codec<T> codec) {
      this.paths = paths;
      this.codec = codec;
    }

    /**
     * Submits one complete definition using its native path.
     *
     * @param id entry identifier.
     * @param value complete entry value.
     */
    public void put(Identifier id, T value) {
      putEncoded(paths.idToFile(id), codec, value);
    }
  }
}
