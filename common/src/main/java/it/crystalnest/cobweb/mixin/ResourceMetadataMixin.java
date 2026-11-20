package it.crystalnest.cobweb.mixin;

import com.google.gson.JsonObject;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import it.crystalnest.cobweb.api.pack.generation.ResourceMetadataJson;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.util.GsonHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.io.InputStream;
import java.io.Reader;

/**
 * Injects into {@link ResourceMetadata} to preserve raw metadata for texture transformations.
 */
@Mixin(ResourceMetadata.class)
public interface ResourceMetadataMixin {
  /**
   * Modifies the return value of {@link GsonHelper#parse(Reader)} inside the method {@link ResourceMetadata#fromJsonStream(InputStream)}.<br>
   * Captures the parsed sidecar while it is still in scope, before the native reader closes.
   *
   * @param original parsed native metadata document.
   * @param metadata capture shared with the return handler.
   * @return unchanged native metadata document.
   */
  @ModifyExpressionValue(method = "fromJsonStream", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/GsonHelper;parse(Ljava/io/Reader;)Lcom/google/gson/JsonObject;"))
  private static JsonObject modifyParse(JsonObject original, @Share("parsedMetadata") LocalRef<JsonObject> metadata) {
    metadata.set(original);
    return original;
  }

  /**
   * Modifies the return value of {@link ResourceMetadata#fromJsonStream(InputStream)}.<br>
   * Retains the sidecar used by the native metadata reader.
   *
   * @param original native metadata reader.
   * @param metadata parsed sidecar retained from the native parse operation.
   * @return reader with the original document.
   */
  @ModifyReturnValue(method = "fromJsonStream", at = @At("RETURN"))
  private static ResourceMetadata modifyFromJsonStream(ResourceMetadata original, @Share("parsedMetadata") LocalRef<JsonObject> metadata) {
    return new ResourceMetadataJson(original, metadata.get());
  }
}
