package it.crystalnest.cobweb.api.pack.client;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.serialization.JsonOps;
import it.crystalnest.cobweb.api.image.client.ImageTransforms;
import it.crystalnest.cobweb.api.pack.GenerationOutput;
import it.crystalnest.cobweb.api.pack.GenerationResources;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Connects declarative texture operations to the shared image helpers.
 */
@ApiStatus.Internal
public final class TextureRules {
  private static final int MAX_PNG_BYTES = 16 * 1024 * 1024;

  private static final long MAX_PIXELS = 1024L * 1024;

  private TextureRules() {}

  /**
   * Recolors a texture while retaining its effective sidecar and frame layout.
   *
   * @param output pack output.
   * @param destination destination PNG path.
   * @param source template PNG.
   * @param palette reference PNG.
   * @param mask optional mask PNG.
   * @throws IOException if opening or encoding an image fails.
   */
  public static void recolor(GenerationOutput output, Identifier destination, GenerationResources.File source, GenerationResources.File palette,
    @Nullable GenerationResources.File mask) throws IOException {
    try (var original = read(source); var reference = read(palette); var region = mask == null ? null : read(mask); var result = ImageTransforms.recolor(original, reference, region)) {
      submit(output, destination, result, metadata(source));
    }
  }

  /**
   * Composites two static images of the same size.
   *
   * @param output pack output.
   * @param destination destination PNG path.
   * @param source background PNG.
   * @param layer foreground PNG.
   * @throws IOException if opening or encoding an image fails.
   */
  public static void overlay(GenerationOutput output, Identifier destination, GenerationResources.File source, GenerationResources.File layer) throws IOException {
    JsonObject metadata = metadata(source);
    if (metadata.has("animation") || metadata(layer).has("animation")) {
      throw new IllegalArgumentException("overlay_texture requires static PNGs");
    }
    try (var background = read(source); var foreground = read(layer); var result = ImageTransforms.overlay(background, foreground)) {
      submit(output, destination, result, metadata);
    }
  }

  private static JsonObject metadata(GenerationResources.File source) throws IOException {
    var raw = source.metadataJson();
    if (raw.isPresent()) {
      return raw.get();
    }
    var metadata = new JsonObject();
    source.metadata(AnimationMetadataSection.TYPE).ifPresent(animation ->
      metadata.add(AnimationMetadataSection.TYPE.name(), AnimationMetadataSection.CODEC.encodeStart(JsonOps.INSTANCE, animation).getOrThrow()));
    return metadata;
  }

  private static void submit(GenerationOutput output, Identifier destination, NativeImage result, JsonObject metadata) throws IOException {
    var id = destination.withPath(path -> path.substring("textures/".length(), path.length() - ".png".length()));
    output.putSprite(id, stream -> AssetResourceKinds.writePng(result, stream), metadata.isEmpty() ? null : metadata);
  }

  private static NativeImage read(GenerationResources.File file) throws IOException {
    try (var input = file.open(MAX_PNG_BYTES)) {
      byte[] png = input.readNBytes(MAX_PNG_BYTES + 1);
      if (png.length < 24 || png.length > MAX_PNG_BYTES) {
        throw new IOException("Incomplete PNG header or PNG exceeds the encoded byte limit of " + MAX_PNG_BYTES);
      }
      var fields = ByteBuffer.wrap(png);
      if (fields.getLong() != 0x89504E470D0A1A0AL || fields.getInt() != 13 || fields.getInt() != 0x49484452) {
        throw new IOException("Invalid PNG header");
      }
      int width = fields.getInt();
      int height = fields.getInt();
      if (width < 1 || height < 1 || (long) width * height > MAX_PIXELS) {
        throw new IOException("PNG exceeds the decoded pixel limit of " + MAX_PIXELS);
      }
      return NativeImage.read(new ByteArrayInputStream(png));
    }
  }
}
