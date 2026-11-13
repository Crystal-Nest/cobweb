package it.crystalnest.cobweb.api.pack.client;

import com.mojang.blaze3d.platform.NativeImage;
import it.crystalnest.cobweb.api.pack.generation.GenerationContext;
import it.crystalnest.cobweb.api.pack.generation.GenerationResources;
import it.crystalnest.cobweb.api.pack.generation.output.ResourceKind;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.locale.DeprecatedTranslationsInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import org.jetbrains.annotations.ApiStatus;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads typed client asset inputs over the callback's native resource snapshot.
 */
public final class AssetGenerationResources {
  private final GenerationResources resources;

  /**
   * Creates a typed view of client resources.
   *
   * @param context scoped client callback.
   * @throws IllegalStateException if this callback generates data.
   */
  @ApiStatus.Internal
  public AssetGenerationResources(GenerationContext context) {
    if (context.phase().type() != PackType.CLIENT_RESOURCES) {
      throw new IllegalStateException("Client assets are unavailable in " + context.phase());
    }
    resources = context.resources();
  }

  /**
   * Reads one namespace's translation stack in native priority order.
   *
   * @param namespace translation namespace.
   * @param language language code such as {@code en_us}.
   * @return detached translation map.
   * @throws IOException if a source file cannot be read.
   */
  public Map<String, String> translations(String namespace, String language) throws IOException {
    return resources.translations(namespace, language);
  }

  /**
   * Resolves translations across the requested languages in native language, namespace and pack order.
   *
   * @param languages language codes ordered from fallback to most preferred.
   * @return detached effective translations with native deprecated-key rules applied.
   * @throws IOException if a source file cannot be read.
   */
  public Map<String, String> translations(List<String> languages) throws IOException {
    Map<String, String> effective = new LinkedHashMap<>();
    for (String language : languages) {
      for (String namespace : resources.namespaces()) {
        effective.putAll(translations(namespace, language));
      }
    }
    DeprecatedTranslationsInfo.loadFromDefaultResource().applyToMap(effective);
    return Map.copyOf(effective);
  }

  /**
   * Decodes the winning PNG and its effective native animation metadata.
   *
   * @param id texture identifier without its directory and extension.
   * @return sprite owned by the caller, or an empty result for a missing texture.
   * @throws IOException if the texture or its metadata cannot be read.
   */
  public Optional<Sprite> sprite(Identifier id) throws IOException {
    var file = resources.get(id.withPath(path -> "textures/" + path + ".png"));
    if (file.isEmpty()) {
      return Optional.empty();
    }
    var animation = file.get().metadata(AnimationMetadataSection.TYPE);
    try (var input = file.get().open()) {
      return Optional.of(new Sprite(NativeImage.read(input), animation));
    }
  }

  /**
   * Finds the winning encoded Ogg resource for a sample.
   *
   * @param id sample identifier without its directory and extension.
   * @return scoped encoded audio file, if present.
   */
  public Optional<GenerationResources.File> sound(Identifier id) {
    return resources.get(Sound.SOUND_LISTER.idToFile(id));
  }

  /**
   * Reads the winning typed definition of a client resource.
   *
   * @param kind client resource kind.
   * @param id logical identifier.
   * @param <T> definition type.
   * @return detached definition if present.
   * @throws IOException if the definition cannot be read.
   */
  public <T> Optional<T> get(ResourceKind<T> kind, Identifier id) throws IOException {
    return resources.get(kind, id);
  }

  /**
   * Contains decoded sprite pixels and native animation settings.
   *
   * @param image caller-owned pixels.
   * @param animation optional animation metadata.
   */
  public record Sprite(NativeImage image, Optional<AnimationMetadataSection> animation) implements AutoCloseable {
    /**
     * Releases the native image.
     */
    @Override
    public void close() {
      image.close();
    }
  }
}
