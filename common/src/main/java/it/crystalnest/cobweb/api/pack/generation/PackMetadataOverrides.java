package it.crystalnest.cobweb.api.pack.generation;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import it.crystalnest.cobweb.platform.services.PackHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackMetadataResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.util.InclusiveRange;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Composes Java description and format declarations with the native primary metadata section.
 */
final class PackMetadataOverrides {
  /**
   * Native metadata field containing the pack description.
   */
  private static final String DESCRIPTION = "description";

  private PackMetadataOverrides() {}

  /**
   * Applies the supplied Java fields and lets the native codec validate the result.
   *
   * @param type resource side.
   * @param resources native bundled metadata, if available.
   * @param description optional Java description.
   * @param formats optional Java format interval.
   * @return effective primary metadata, or {@code null} if absent.
   * @throws IOException if the bundled file cannot be read.
   */
  @Nullable
  static PackMetadataSection read(PackType type, @Nullable PackMetadataResources resources, @Nullable Component description, @Nullable InclusiveRange<PackFormat> formats) throws IOException {
    var section = PackMetadataSection.forPackType(type);
    if (description == null && formats == null) {
      return resources == null ? null : resources.getMetadataSection(section);
    }
    if (description != null && formats != null) {
      return new PackMetadataSection(description, formats);
    }
    var root = PackHelper.readMetadataFile(resources);
    JsonObject json = root.has(section.name()) ? root.getAsJsonObject(section.name()) : new JsonObject();
    var codec = section.codec();
    var format = formats == null ? new InclusiveRange<>(PackFormat.of(Integer.MAX_VALUE)) : formats;
    var overlay = codec.encodeStart(JsonOps.INSTANCE, new PackMetadataSection(description == null ? Component.empty() : description, format)).getOrThrow().getAsJsonObject();
    if (description != null) {
      json.add(DESCRIPTION, overlay.get(DESCRIPTION));
    } else {
      for (String name : new String[]{"pack_format", "supported_formats", "min_format", "max_format"}) {
        json.remove(name);
      }
      overlay.remove(DESCRIPTION);
      for (var entry : overlay.entrySet()) {
        json.add(entry.getKey(), entry.getValue());
      }
    }
    return codec.parse(JsonOps.INSTANCE, json).getOrThrow();
  }
}
