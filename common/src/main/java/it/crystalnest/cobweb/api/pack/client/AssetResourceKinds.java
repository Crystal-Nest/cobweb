package it.crystalnest.cobweb.api.pack.client;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.serialization.JsonOps;
import it.crystalnest.cobweb.api.pack.GenerationOutput;
import it.crystalnest.cobweb.api.pack.Phase;
import it.crystalnest.cobweb.api.pack.ResourceKind;
import it.crystalnest.cobweb.api.pack.ResourceLifetime;
import it.crystalnest.cobweb.mixin.client.NativeImageMixin;
import net.minecraft.client.data.models.model.ModelInstance;
import net.minecraft.client.gui.font.providers.GlyphProviderDefinition;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelDispatcher;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSources;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.util.List;

/**
 * Encodes client definitions with the formats used by Minecraft's native asset readers.
 * Use this class from client initialization and asset generation.
 */
public final class AssetResourceKinds {
  /**
   * Blockstate model dispatch definitions.
   */
  public static final ResourceKind<BlockStateModelDispatcher> BLOCKSTATES = ResourceKind.json("blockstates", BlockStateModelDispatcher.CODEC, ResourceLifetime.RELOADABLE, Phase.CLIENT_RESOURCES);

  /**
   * Client item model definitions.
   */
  public static final ResourceKind<ClientItem> ITEMS = ResourceKind.json("items", ClientItem.CODEC, ResourceLifetime.RELOADABLE, Phase.CLIENT_RESOURCES);

  /**
   * Atlas sprite source definitions.
   */
  public static final ResourceKind<List<SpriteSource>> ATLASES = ResourceKind.json("atlases", SpriteSources.FILE_CODEC, ResourceLifetime.RELOADABLE, Phase.CLIENT_RESOURCES);

  /**
   * Conditional glyph providers for one font.
   */
  public static final ResourceKind<List<GlyphProviderDefinition.Conditional>> FONTS = ResourceKind.json("font", GlyphProviderDefinition.Conditional.CODEC.listOf().fieldOf("providers").codec(),
    ResourceLifetime.RELOADABLE, Phase.CLIENT_RESOURCES);

  /**
   * Particle sprite identifiers for one particle description.
   */
  public static final ResourceKind<List<Identifier>> PARTICLES = ResourceKind.json("particles", Identifier.CODEC.listOf().optionalFieldOf("textures", List.of()).codec(),
    ResourceLifetime.RELOADABLE, Phase.CLIENT_RESOURCES);

  private AssetResourceKinds() {}

  /**
   * Submits a model produced by Minecraft's model generation API.
   *
   * @param output asset generation output.
   * @param id model identifier without its directory and extension.
   * @param model native generated model.
   */
  public static void putModel(GenerationOutput output, Identifier id, ModelInstance model) {
    output.putJson(id.withPath(path -> "models/" + path + ".json"), model.get());
  }

  /**
   * Encodes a caller-owned image and its optional native animation as one sprite contribution.
   *
   * @param output asset generation output.
   * @param id texture identifier without its directory and extension.
   * @param image pixels retained by the caller.
   * @param animation optional native animation settings.
   * @throws IOException if the image cannot be encoded.
   */
  public static void putSprite(GenerationOutput output, Identifier id, NativeImage image, @Nullable AnimationMetadataSection animation) throws IOException {
    JsonObject sidecar = null;
    if (animation != null) {
      sidecar = new JsonObject();
      sidecar.add(AnimationMetadataSection.TYPE.name(), AnimationMetadataSection.CODEC.encodeStart(JsonOps.INSTANCE, animation).getOrThrow());
    }
    output.putSprite(id, stream -> writePng(image, stream), sidecar);
  }

  static void writePng(NativeImage image, OutputStream stream) throws IOException {
    var nativeImage = (NativeImageMixin) (Object) image;
    nativeImage.invokeCheckAllocated();
    if (!nativeImage.invokeWriteToChannel(Channels.newChannel(stream))) {
      throw new IOException("Unable to encode sprite");
    }
  }
}
