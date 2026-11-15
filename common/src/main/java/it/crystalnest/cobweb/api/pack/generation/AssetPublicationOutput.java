package it.crystalnest.cobweb.api.pack.generation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import it.crystalnest.cobweb.api.pack.generation.output.ResourceLifetime;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads bundled assets and submits complete files for one server publication callback.
 * The view lasts for that callback on the publication worker. Published assets are built separately from the local client resource load.
 */
public final class AssetPublicationOutput {
  /**
   * The pack opening that supplies bundled assets and receives the generated files.
   */
  private final GeneratedPackResources resources;

  /**
   * Pending publication files keyed by their resource ID.
   */
  private final Map<Identifier, GeneratedPackResources.File> files = new HashMap<>();

  /**
   * Owns pending file backing until the pack opening accepts it.
   */
  private final PackFileOwner owned = new PackFileOwner();

  /**
   * Whether the callback can still read and submit assets.
   */
  private boolean open = true;

  /**
   * @param resources pack opening for this publication callback.
   */
  AssetPublicationOutput(GeneratedPackResources resources) {
    this.resources = resources;
  }

  /**
   * Reads a bundled asset through native overlay precedence.
   * The input stream is closed when the reader returns.
   *
   * @param id namespaced path including its extension.
   * @param reader reads the asset within this call.
   * @param <T> result type.
   * @return reader result, or {@code null} if the asset is absent.
   * @throws IOException if opening or reading the asset fails.
   */
  @Nullable
  public <T> T readBundled(Identifier id, Reader<T> reader) throws IOException {
    checkOpen();
    var resource = resources.getResource(PackType.CLIENT_RESOURCES, id);
    if (resource == null) {
      return null;
    }
    try (var input = resource.get()) {
      return reader.read(input);
    }
  }

  /**
   * Submits a complete file. A subsequent submission of the same path replaces this one.
   *
   * @param id namespaced path including its extension.
   * @param writer writes the complete resource within this call.
   * @throws IOException if the writer fails.
   */
  public void write(Identifier id, GenerationOutput.FileWriter writer) throws IOException {
    checkOpen();
    PackPaths.resourcePath(PackType.CLIENT_RESOURCES, id);
    var file = PackFile.write(writer);
    owned.adopt(file);
    var previous = files.put(id, new GeneratedPackResources.File(file, ResourceLifetime.RELOADABLE));
    if (previous != null) {
      owned.release(previous.content());
    }
  }

  /**
   * Submits a complete UTF-8 JSON file, such as a model definition.
   *
   * @param id namespaced path including its extension.
   * @param value JSON contents.
   * @throws IOException if writing the file fails.
   */
  public void putJson(Identifier id, JsonElement value) throws IOException {
    byte[] bytes = PackIO.json(value);
    write(id, stream -> stream.write(bytes));
  }

  /**
   * Submits a complete translation file for one namespace and language.
   *
   * @param namespace resource namespace.
   * @param language language identifier such as {@code en_us}.
   * @param translations complete translation mappings.
   * @throws IOException if writing the file fails.
   */
  public void putTranslations(String namespace, String language, Map<String, String> translations) throws IOException {
    var json = new JsonObject();
    translations.forEach(json::addProperty);
    putJson(Identifier.fromNamespaceAndPath(namespace, "lang/" + language + ".json"), json);
  }

  /**
   * Submits an encoded PNG in the texture directory.
   *
   * @param texture texture identifier without the directory and extension.
   * @param writer writes complete PNG bytes.
   * @throws IOException if writing the file fails.
   */
  public void putTexture(Identifier texture, GenerationOutput.FileWriter writer) throws IOException {
    write(PackPaths.texture(texture), writer);
  }

  /**
   * Submits encoded Ogg Vorbis audio in the sound directory.
   *
   * @param sound sound identifier without the directory and extension.
   * @param writer writes complete audio bytes.
   * @throws IOException if writing the file fails.
   */
  public void putSound(Identifier sound, GenerationOutput.FileWriter writer) throws IOException {
    write(PackPaths.sound(sound), writer);
  }

  /**
   * Transfers pending file ownership to the pack opening and makes the files visible.
   */
  void finish() {
    resources.adopt(owned);
    resources.publish(files);
    files.clear();
  }

  /**
   * Closes the callback view and releases any files it still owns.
   */
  void close() {
    open = false;
    owned.close();
    files.clear();
  }

  /**
   * Checks that the publication callback can still use this view.
   *
   * @throws IllegalStateException if the callback has ended.
   */
  private void checkOpen() {
    if (!open) {
      throw new IllegalStateException("Asset publication callback has ended");
    }
  }

  /**
   * Reads a bundled asset within its publication callback.
   *
   * @param <T> result type.
   */
  @FunctionalInterface
  public interface Reader<T> {
    /**
     * Reads an asset before its stream is closed.
     *
     * @param input bundled asset stream.
     * @return detached result.
     * @throws IOException if reading fails.
     */
    T read(InputStream input) throws IOException;
  }
}
