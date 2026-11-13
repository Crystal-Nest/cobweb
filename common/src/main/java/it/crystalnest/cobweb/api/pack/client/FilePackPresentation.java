package it.crystalnest.cobweb.api.pack.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import it.crystalnest.cobweb.Constants;
import it.crystalnest.cobweb.api.pack.PackMetadataOverrides;
import it.crystalnest.cobweb.api.pack.dependency.Mods;
import it.crystalnest.cobweb.api.pack.presentation.PackIcon;
import it.crystalnest.cobweb.api.pack.presentation.PackPresentation;
import it.crystalnest.cobweb.api.pack.query.PackQueries;
import it.crystalnest.cobweb.api.pack.query.PackQuestion;
import it.crystalnest.cobweb.api.pack.query.PackReference;
import it.crystalnest.cobweb.api.pack.query.PackStatus;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.Pack;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Set;

/**
 * Evaluates the file-based display metadata for a pack editor.
 */
final class FilePackPresentation {
  private FilePackPresentation() {}

  /**
   * Reads base and conditional presentation from the owning pack.
   *
   * @param pack displayed pack.
   * @param queries current editor observations.
   * @param evaluateConditions whether to apply file conditions.
   * @return file fields, or null when absent or invalid.
   */
  @Nullable
  static PackPresentation read(Pack pack, PackQueries queries, boolean evaluateConditions) {
    try (var metadata = pack.openMetadata()) {
      var root = PackMetadataOverrides.readFile(metadata);
      if (!root.has("cobweb")) {
        return null;
      }
      var section = root.getAsJsonObject("cobweb");
      Component title = text(section, "title");
      var result = PackPresentation.builder();
      if (title != null) {
        result.title(title);
      }
      if (evaluateConditions && section.has("presentation")) {
        var base = result.build();
        try {
          var self = PackReference.nativeId(pack.getId());
          var entries = section.getAsJsonArray("presentation");
          if (entries.size() > 128) {
            throw new IllegalArgumentException("Too many presentation entries");
          }
          for (var value : entries) {
            apply(value.getAsJsonObject(), self, queries, result);
          }
        } catch (RuntimeException failure) {
          Constants.LOGGER.warn("Unable to evaluate file presentation for {}", pack.getId(), failure);
          return base;
        }
      }
      return result.build();
    } catch (IOException | RuntimeException failure) {
      Constants.LOGGER.warn("Unable to read file presentation for {}", pack.getId(), failure);
      return null;
    }
  }

  private static void apply(JsonObject entry, PackReference self, PackQueries queries, PackPresentation.Builder result) {
    if (!Set.of("when", "title", "description", "icon").containsAll(entry.keySet())) {
      throw new IllegalArgumentException("Unknown presentation field");
    }
    Component title = text(entry, "title");
    Component description = text(entry, "description");
    PackIcon icon = entry.has("icon") ? PackIcon.bundledPack(string(entry, "icon")) : null;
    var conditions = entry.getAsJsonArray("when");
    if (conditions == null || conditions.isEmpty() || conditions.size() > 32) {
      throw new IllegalArgumentException("Expected 1 to 32 presentation predicates");
    }
    boolean matches = true;
    for (var condition : conditions) {
      matches &= matches(condition.getAsJsonObject(), self, queries);
    }
    if (matches) {
      if (title != null) {
        result.title(title);
      }
      if (description != null) {
        result.description(description);
      }
      if (icon != null) {
        result.icon(icon);
      }
    }
  }

  private static boolean matches(JsonObject condition, PackReference self, PackQueries queries) {
    if (condition.keySet().equals(Set.of("mod_loaded"))) {
      return Mods.isLoaded(string(condition, "mod_loaded"));
    }
    if (!condition.keySet().equals(Set.of("pack", "is"))) {
      throw new IllegalArgumentException("Expected a mod_loaded or pack predicate");
    }
    String target = string(condition, "pack");
    if (!target.equals("self") && !target.contains(":")) {
      throw new IllegalArgumentException("Expected a namespaced pack ID");
    }
    var reference = target.equals("self") ? self : PackReference.logical(Identifier.parse(target));
    PackQuestion question = switch (string(condition, "is")) {
      case "available" -> PackQuestion.AVAILABLE;
      case "edited_selected" -> PackQuestion.EDITED_SELECTED;
      default -> throw new IllegalArgumentException("Expected available or edited_selected");
    };
    return queries.query(reference, question) == PackStatus.YES;
  }

  @Nullable
  private static Component text(JsonObject object, String field) {
    JsonElement value = object.get(field);
    return value == null ? null : ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, value).getOrThrow();
  }

  private static String string(JsonObject object, String field) {
    JsonElement value = object.get(field);
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || value.getAsString().isBlank()) {
      throw new IllegalArgumentException("Expected a nonempty string for " + field);
    }
    return value.getAsString();
  }
}
