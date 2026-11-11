package it.crystalnest.cobweb.mixin.client;

import com.mojang.blaze3d.platform.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.io.IOException;
import java.nio.channels.WritableByteChannel;

/**
 * Injects into {@link NativeImage} to encode PNGs directly into pack backing streams.
 */
@Mixin(NativeImage.class)
public interface NativeImageMixin {
  /**
   * Verifies that the image still owns its native pixels before encoding.
   */
  @Invoker("checkAllocated")
  void invokeCheckAllocated();

  /**
   * Invokes the native PNG encoder without a temporary image file.
   *
   * @param output destination channel.
   * @return whether the native encoder succeeded.
   * @throws IOException if writing fails.
   */
  @Invoker("writeToChannel")
  boolean invokeWriteToChannel(WritableByteChannel output) throws IOException;
}
