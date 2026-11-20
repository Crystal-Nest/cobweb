package it.crystalnest.cobweb.api.pack.presentation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import it.crystalnest.cobweb.Constants;
import it.crystalnest.cobweb.api.pack.query.PackQueries;
import it.crystalnest.cobweb.api.pack.query.PackQuestion;
import it.crystalnest.cobweb.api.pack.query.PackReference;
import it.crystalnest.cobweb.api.pack.query.PackStatus;
import it.crystalnest.cobweb.api.platform.PlatformQueries;
import it.crystalnest.cobweb.platform.services.PackHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.Pack;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Set;

/**
 * Reads Cobweb's base title and conditional presentation fields from a pack's own metadata.
 */
final class FilePackPresentation {
  /**
   * Cobweb metadata field containing the pack title.
   */
  private static final String TITLE = "title";

  private FilePackPresentation() {}

  /**
   * Reads the base title and, when enabled, applies matching presentation entries in file order.
   * If a conditional entry is invalid, logs the failure and returns the base presentation.
   *
   * @param pack displayed pack.
   * @param queries current editor observations.
   * @param evaluateConditions whether to evaluate conditional entries for this pack.
   * @return file overrides, or {@code null} if the Cobweb section is absent or the base metadata cannot be read.
   */
  @Nullable
  static PackPresentation read(Pack pack, PackQueries queries, boolean evaluateConditions) {
    try (var metadata = pack.open()) {
      var root = PackHelper.readMetadataFile(metadata);
      if (root.has(Constants.MOD_ID)) {
        var section = root.getAsJsonObject(Constants.MOD_ID);
        Component title = text(section, TITLE);
        var result = PackPresentation.builder();
        if (title != null) {
          result.title(title);
        }
        return evaluateConditions && section.has("presentation") ? evaluatePresentation(section, pack, queries, result) : result.build();
      }
      return null;
    } catch (IOException | RuntimeException failure) {
      Constants.LOGGER.error("Unable to read file presentation for {}", pack.getId(), failure);
      return null;
    }
  }

  /**
   * Evaluates conditional entries in file order and preserves the base fields if evaluation fails.
   *
   * @param section owned Cobweb metadata section.
   * @param pack displayed pack.
   * @param queries captured editor observations.
   * @param result builder containing the base presentation.
   * @return evaluated presentation, or its base value after a logged failure.
   */
  private static PackPresentation evaluatePresentation(JsonObject section, Pack pack, PackQueries queries, PackPresentation.Builder result) {
    var base = result.build();
    try {
      var entries = section.getAsJsonArray("presentation");
      if (entries.size() > 128) {
        throw new IllegalArgumentException("Too many presentation entries");
      }
      var self = PackReference.nativeId(pack.getId());
      for (var value : entries) {
        apply(value.getAsJsonObject(), self, queries, result);
      }
      return result.build();
    } catch (RuntimeException failure) {
      Constants.LOGGER.error("Unable to evaluate file presentation for {}", pack.getId(), failure);
      return base;
    }
  }

  /**
   * Applies the supplied fields of one presentation entry when all its predicates match.
   *
   * @param entry file presentation entry.
   * @param self native reference to the displayed pack.
   * @param queries captured observations of the current editor.
   * @param result presentation builder to update.
   * @throws IllegalArgumentException if the entry contains unknown fields or an invalid predicate count.
   */
  private static void apply(JsonObject entry, PackReference self, PackQueries queries, PackPresentation.Builder result) {
    if (!Set.of("when", TITLE, "description", "icon").containsAll(entry.keySet())) {
      throw new IllegalArgumentException("Unknown presentation field");
    }
    var conditions = entry.getAsJsonArray("when");
    if (conditions == null || conditions.isEmpty() || conditions.size() > 32) {
      throw new IllegalArgumentException("Expected 1 to 32 presentation predicates");
    }
    boolean matches = true;
    for (var condition : conditions) {
      matches &= matches(condition.getAsJsonObject(), self, queries);
    }
    Component title = text(entry, TITLE);
    Component description = text(entry, "description");
    PackIcon icon = entry.has("icon") ? PackIcon.bundledPack(string(entry, "icon")) : null;
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

  /**
   * Checks a mod-presence or pack-selection predicate against the current editor observations.
   *
   * @param condition predicate object.
   * @param self reference to the displayed pack.
   * @param queries captured observations of the current editor.
   * @return whether the mod is loaded or the pack satisfies the requested observation.
   * @throws IllegalArgumentException if the predicate fields, target, or observation are unsupported.
   */
  private static boolean matches(JsonObject condition, PackReference self, PackQueries queries) {
    if (condition.keySet().equals(Set.of("mod_loaded"))) {
      return PlatformQueries.isModLoaded(string(condition, "mod_loaded"));
    }
    if (!condition.keySet().equals(Set.of("pack", "is"))) {
      throw new IllegalArgumentException("Expected a mod_loaded or pack predicate");
    }
    String target = string(condition, "pack");
    if (!"self".equals(target) && !target.contains(":")) {
      throw new IllegalArgumentException("Expected a namespaced pack ID");
    }
    PackQuestion question = switch (string(condition, "is")) {
      case "available" -> PackQuestion.AVAILABLE;
      case "edited_selected" -> PackQuestion.EDITED_SELECTED;
      default -> throw new IllegalArgumentException("Expected available or edited_selected");
    };
    var reference = "self".equals(target) ? self : PackReference.logical(Identifier.parse(target));
    return queries.query(reference, question) == PackStatus.YES;
  }

  /**
   * Decodes a field using Minecraft's text component codec.
   *
   * @param object containing metadata object.
   * @param field component field name.
   * @return decoded component, or {@code null} if the field is absent.
   */
  @Nullable
  private static Component text(JsonObject object, String field) {
    JsonElement value = object.get(field);
    return value == null ? null : ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, value).getOrThrow();
  }

  /**
   * Reads a required nonblank string field without coercing other JSON values.
   *
   * @param object containing metadata object.
   * @param field required field name.
   * @return field value.
   * @throws IllegalArgumentException if the field is absent, blank, or has a different JSON type.
   */
  private static String string(JsonObject object, String field) {
    JsonElement value = object.get(field);
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || value.getAsString().isBlank()) {
      throw new IllegalArgumentException("Expected a nonempty string for " + field);
    }
    return value.getAsString();
  }
}
