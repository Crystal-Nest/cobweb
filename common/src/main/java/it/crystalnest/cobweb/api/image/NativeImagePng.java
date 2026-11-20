package it.crystalnest.cobweb.api.image;

import com.mojang.blaze3d.platform.NativeImage;
import it.crystalnest.cobweb.mixin.client.NativeImageMixin;
import org.jetbrains.annotations.ApiStatus;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;

/**
 * Streams Minecraft's PNG encoder into client pack backing storage.
 */
@ApiStatus.Internal
public final class NativeImagePng {
  private NativeImagePng() {}

  /**
   * Encodes a native image as PNG. The caller owns the image and destination stream.
   *
   * @param image allocated native image.
   * @param stream destination stream.
   * @throws IOException if the image cannot be encoded.
   */
  public static void write(NativeImage image, OutputStream stream) throws IOException {
    var nativeImage = (NativeImageMixin) (Object) image;
    nativeImage.invokeCheckAllocated();
    if (!nativeImage.invokeWriteToChannel(Channels.newChannel(stream))) {
      throw new IOException("Unable to encode sprite");
    }
  }
}
