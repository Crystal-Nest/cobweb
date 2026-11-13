package it.crystalnest.cobweb.api.pack;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackMetadataResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.util.InclusiveRange;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Composes Java description and format declarations with the native primary metadata section.
 */
@ApiStatus.Internal
public final class PackMetadataOverrides {
  private PackMetadataOverrides() {}

  /**
   * Applies the supplied Java fields and lets the native codec validate the result.
   *
   * @param type resource side.
   * @param resources native bundled metadata, if available.
   * @param description optional Java description.
   * @param formats optional Java format interval.
   * @return effective primary metadata, or null if absent.
   * @throws IOException if the bundled file cannot be read.
   */
  @Nullable
  public static PackMetadataSection read(PackType type, @Nullable PackMetadataResources resources, @Nullable Component description, @Nullable InclusiveRange<PackFormat> formats) throws IOException {
    var section = PackMetadataSection.forPackType(type);
    if (description == null && formats == null) {
      return resources == null ? null : resources.getMetadataSection(section);
    }
    if (description != null && formats != null) {
      return new PackMetadataSection(description, formats);
    }
    var root = readFile(resources);
    JsonObject json = root.has(section.name()) ? root.getAsJsonObject(section.name()) : new JsonObject();
    var codec = section.codec();
    var format = formats == null ? new InclusiveRange<>(PackFormat.of(Integer.MAX_VALUE)) : formats;
    var overlay = codec.encodeStart(JsonOps.INSTANCE, new PackMetadataSection(description == null ? Component.empty() : description, format)).getOrThrow().getAsJsonObject();
    if (description != null) {
      json.add("description", overlay.get("description"));
    } else {
      for (String name : new String[]{"pack_format", "supported_formats", "min_format", "max_format"}) {
        json.remove(name);
      }
      overlay.remove("description");
      for (var entry : overlay.entrySet()) {
        json.add(entry.getKey(), entry.getValue());
      }
    }
    return codec.parse(JsonOps.INSTANCE, json).getOrThrow();
  }

  /**
   * Reads the pack's own metadata document.
   *
   * @param resources opened primary resources, if any.
   * @return detached metadata document, or an empty object if absent.
   * @throws IOException if the file cannot be read.
   */
  public static JsonObject readFile(@Nullable PackMetadataResources resources) throws IOException {
    var source = resources == null ? null : resources.getRootResource("pack.mcmeta");
    if (source != null) {
      try (var reader = new InputStreamReader(source.get(), StandardCharsets.UTF_8)) {
        return JsonParser.parseReader(reader).getAsJsonObject();
      }
    }
    return new JsonObject();
  }
}
