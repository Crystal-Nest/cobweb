package it.crystalnest.cobweb.mixin;

import com.google.gson.JsonObject;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import it.crystalnest.cobweb.impl.pack.ResourceMetadataJson;
import net.minecraft.server.packs.resources.ResourceMetadata;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.io.InputStream;

/**
 * Injects into {@link ResourceMetadata} to preserve raw metadata for texture transformations.
 */
@Mixin(ResourceMetadata.class)
public interface ResourceMetadataMixin {
  /**
   * Modifies the return value of {@link ResourceMetadata#fromJsonStream(InputStream)}.<br>
   * Retains the sidecar used by the native metadata reader.
   *
   * @param original native metadata reader.
   * @param metadata parsed sidecar.
   * @return reader with the original document.
   */
  @ModifyReturnValue(method = "fromJsonStream", at = @At("RETURN"))
  private static ResourceMetadata modifyFromJsonStream(ResourceMetadata original, @Local JsonObject metadata) {
    return new ResourceMetadataJson(original, metadata);
  }
}
