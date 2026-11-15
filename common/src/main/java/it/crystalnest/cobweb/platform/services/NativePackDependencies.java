package it.crystalnest.cobweb.platform.services;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.crystalnest.cobweb.Constants;
import it.crystalnest.cobweb.api.pack.dependency.ModRequirement;
import it.crystalnest.cobweb.api.pack.dependency.PackRequirement;
import it.crystalnest.cobweb.api.pack.dependency.VersionRange;
import it.crystalnest.cobweb.api.pack.query.PackInfo;
import it.crystalnest.cobweb.api.pack.query.PackReference;
import it.crystalnest.cobweb.platform.Services;
import it.crystalnest.cobweb.platform.services.PackHelper.PackDeclaration;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackMetadataResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Reads pack identity and dependency metadata, validates native selections, and writes effective dependency settings for inspection exports.
 */
final class NativePackDependencies {
  /**
   * Pack service that owns the registrations used for identity and dependency resolution.
   */
  private final PackHelper registrations;

  /**
   * @param registrations pack service that owns the registered declarations.
   */
  NativePackDependencies(PackHelper registrations) {
    this.registrations = registrations;
  }

  /**
   * Writes the effective registered identity and Java dependency overrides into an inspection export.
   *
   * @param document detached pack metadata to update in place.
   * @param declaration registered pack declaration.
   */
  static void exportMetadata(JsonObject document, PackDeclaration declaration) {
    var section = object(document, "cobweb");
    section.addProperty("id", declaration.id().toString());
    if (declaration.dependencyVersion() != null) {
      section.addProperty("version", declaration.dependencyVersion());
    }
    if (declaration.requiredPacks() != null || declaration.requiredMods() != null) {
      var requires = object(section, "requires");
      if (declaration.requiredPacks() != null) {
        var list = new JsonArray();
        for (var value : declaration.requiredPacks()) {
          list.add(switch (value.pack()) {
            case PackReference.Logical logical -> dependencyJson("id", logical.id().toString(), value.version());
            case PackReference.Native nativeRef -> dependencyJson("native_id", nativeRef.id(), value.version());
          });
        }
        requires.add("packs", list);
      }
      if (declaration.requiredMods() != null) {
        var list = new JsonArray();
        for (var value : declaration.requiredMods()) {
          list.add(dependencyJson("id", value.id(), value.version()));
        }
        requires.add("mods", list);
      }
      section.add("requires", requires);
    }
    document.add("cobweb", section);
  }

  /**
   * Serializes a requirement target and its optional version constraint for an inspection export.
   *
   * @param kind metadata field for the target identity.
   * @param id value of the target identity.
   * @param range version constraint, or {@code null} for a presence-only dependency.
   * @return serialized requirement.
   */
  private static JsonObject dependencyJson(String kind, String id, @Nullable VersionRange range) {
    var result = new JsonObject();
    result.addProperty(kind, id);
    if (range != null) {
      var version = new JsonObject();
      if (range.exact() != null) {
        version.addProperty("exact", range.exact());
      }
      if (range.minimum() != null) {
        version.addProperty(range.minimum().inclusive() ? "min" : "min_exclusive", range.minimum().version());
      }
      if (range.maximum() != null) {
        version.addProperty(range.maximum().inclusive() ? "max" : "max_exclusive", range.maximum().version());
      }
      result.add("version", version);
    }
    return result;
  }

  /**
   * Prefers the registered logical identity and falls back to the pack's primary metadata.
   * Unreadable or invalid identity metadata leaves the observation with only its native ID.
   *
   * @param type native pack type.
   * @param nativeId native repository identity.
   * @param resources opened primary metadata.
   * @return pack identity, including a logical identity when available.
   */
  PackInfo info(PackType type, String nativeId, PackMetadataResources resources) {
    var declaration = registrations.packDeclaration(type, nativeId);
    if (declaration != null) {
      return new PackInfo(nativeId, declaration.id());
    }
    var source = resources.getRootResource("pack.mcmeta");
    if (source != null) {
      try (var reader = new InputStreamReader(source.get(), StandardCharsets.UTF_8)) {
        String id = string(section(reader), "id");
        if (id != null) {
          return new PackInfo(nativeId, logicalId(id));
        }
      } catch (IOException | RuntimeException failure) {
        Constants.LOGGER.debug("Unable to observe logical identity of pack {}", nativeId, failure);
      }
    }
    return new PackInfo(nativeId, null);
  }

  /**
   * Reads mod-root declarations for this load and validates them alongside selected pack requirements.
   * Resolves pack requirements to native IDs for generation ordering.
   *
   * @param type native pack side being loaded.
   * @param packs selected native stack in increasing priority.
   * @return selected dependency IDs indexed by the requiring native pack ID.
   * @throws IllegalStateException if a requirement is unsatisfied or ambiguous.
   * @throws UncheckedIOException if pack or mod-root metadata cannot be read.
   */
  Map<String, List<String>> validate(PackType type, List<PackResources> packs) {
    return validate(type, packs, readModSections(), false);
  }

  /**
   * Validates the dependencies declared by asset packs selected for server delivery.
   * Rejects client mod requirements because delivery also supports clients without Cobweb.
   *
   * @param packs selected packs in increasing publication priority.
   * @throws IllegalStateException if a requirement is unsatisfied or ambiguous, or an asset pack requires client mods.
   */
  void validatePublication(List<PackResources> packs) {
    validate(PackType.CLIENT_RESOURCES, packs, Map.of(), true);
  }

  /**
   * Validates dependencies against the given selection and collects each selected pack's resolved pack dependencies.
   *
   * @param type native pack type.
   * @param packs selected pack stack in increasing priority.
   * @param modSections mod-root Cobweb metadata indexed by mod ID.
   * @param publication whether the selection is meant for server delivery to clients.
   * @return resolved dependency IDs indexed by the requiring native pack ID.
   * @throws IllegalStateException if a dependency fails or delivery would require client mods.
   */
  private Map<String, List<String>> validate(PackType type, List<PackResources> packs, Map<String, JsonObject> modSections, boolean publication) {
    Map<String, Selected> selected = new LinkedHashMap<>();
    for (PackResources resources : packs) {
      selected.computeIfAbsent(resources.packId(), ignored -> discover(type, resources, modSections));
    }
    Map<String, List<String>> result = new LinkedHashMap<>();
    selected.forEach((owner, declaration) -> {
      List<String> resolved = new ArrayList<>();
      for (PackRequirement requirement : declaration.packs) {
        resolved.add(resolve(owner, requirement, selected));
      }
      if (publication && !declaration.mods.isEmpty()) {
        throw new IllegalStateException("Cannot deliver pack " + owner + ": client mod requirements cannot be verified for clients without Cobweb: " + declaration.mods);
      }
      for (ModRequirement requirement : declaration.mods) {
        if (!Services.PLATFORM.isModLoaded(requirement.id()) || requirement.version() != null && !Services.PLATFORM.matchesModVersion(requirement.id(), requirement.version())) {
          throw new IllegalStateException("Pack " + owner + " requires mod " + requirement);
        }
      }
      result.put(owner, List.copyOf(resolved));
    });
    String category = type == PackType.SERVER_DATA ? "data_packs" : "asset_packs";
    modSections.forEach((mod, section) -> {
      for (PackRequirement requirement : list(object(section, "requires"), category, NativePackDependencies::requirePack)) {
        resolve("mod " + mod, requirement, selected);
      }
    });
    return Map.copyOf(result);
  }

  /**
   * Reads the Cobweb section of each loaded mod's root metadata when present.
   *
   * @return mod-root metadata indexed by loader mod ID.
   * @throws IllegalArgumentException if a declaration has an invalid owner field, uses pack-owned requirement keys, or shares a source with another mod.
   * @throws UncheckedIOException if the metadata cannot be read.
   */
  private static Map<String, JsonObject> readModSections() {
    Map<String, JsonObject> sections = new LinkedHashMap<>();
    Map<URI, String> owners = new LinkedHashMap<>();
    for (String mod : Services.PLATFORM.loadedModIds()) {
      var source = Services.PLATFORM.modRootPackMetadata(mod);
      if (source == null) {
        continue;
      }
      try (var reader = new InputStreamReader(source.stream().get(), StandardCharsets.UTF_8)) {
        JsonObject section = section(reader);
        if (section.has("id") || section.has("version")) {
          throw new IllegalArgumentException("Mod root cobweb metadata cannot declare id or version: " + mod);
        }
        if (section.has("requires")) {
          String previous = owners.putIfAbsent(source.location(), mod);
          if (previous != null) {
            throw new IllegalArgumentException("Ambiguous mod root pack metadata for " + previous + " and " + mod);
          }
          JsonObject requires = object(section, "requires");
          if (requires.has("packs") || requires.has("mods")) {
            throw new IllegalArgumentException("Mod root pack metadata requires data_packs or asset_packs: " + mod);
          }
        }
        sections.put(mod, section);
      } catch (IOException failure) {
        throw new UncheckedIOException("Unable to read mod-root pack metadata of " + mod, failure);
      }
    }
    return sections;
  }

  /**
   * Reads the dependency declaration for a selected pack and applies any registered Java overrides.
   * Recognizes mod-root metadata exposed through the native repository so the mod's own requirements are evaluated once.
   *
   * @param type native pack type.
   * @param resources opened pack resources.
   * @param modSections mod-root metadata already read for this load.
   * @return effective pack identity, version and requirements.
   * @throws IllegalArgumentException if the pack declares invalid metadata or attempts to override its registered identity.
   * @throws UncheckedIOException if the metadata cannot be read.
   */
  private Selected discover(PackType type, PackResources resources, Map<String, JsonObject> modSections) {
    JsonObject metadata = new JsonObject();
    var root = resources.getRootResource("pack.mcmeta");
    if (root != null) {
      try (var reader = new InputStreamReader(root.get(), StandardCharsets.UTF_8)) {
        metadata = section(reader);
      } catch (IOException failure) {
        throw new UncheckedIOException("Unable to read pack metadata of " + resources.packId(), failure);
      }
    }
    var java = registrations.packDeclaration(type, resources.packId());
    if (java == null && !resources.packId().startsWith("file/") && modSections.containsValue(metadata)) {
      return new Selected(null, null, List.of(), List.of());
    }
    String id = string(metadata, "id");
    Identifier logicalId = id == null ? null : logicalId(id);
    String version = string(metadata, "version");
    if (version != null && version.isBlank()) {
      throw new IllegalArgumentException("Blank pack version for " + resources.packId());
    }
    JsonObject requires = object(metadata, "requires");
    if (requires.has("data_packs") || requires.has("asset_packs")) {
      throw new IllegalArgumentException("Pack dependencies use packs or mods: " + resources.packId());
    }
    List<PackRequirement> packs = java == null ? null : java.requiredPacks();
    List<ModRequirement> mods = java == null ? null : java.requiredMods();
    if (java != null) {
      if (logicalId != null && !java.id().equals(logicalId)) {
        throw new IllegalArgumentException("Metadata cannot change the logical ID of " + resources.packId() + " to " + logicalId);
      }
      logicalId = java.id();
      if (java.dependencyVersion() != null) {
        if (version != null && !version.equals(java.dependencyVersion())) {
          Constants.LOGGER.warn("Using the Java dependency version of {} instead of {}", resources.packId(), version);
        }
        version = java.dependencyVersion();
      }
    }
    return new Selected(logicalId, version, packs == null ? list(requires, "packs", NativePackDependencies::requirePack) : packs,
      mods == null ? list(requires, "mods", NativePackDependencies::requireMod) : mods);
  }

  /**
   * Resolves a requirement to exactly one selected pack and checks the requested version.
   *
   * @param owner name of the requiring pack or mod used in diagnostics.
   * @param requirement target pack and optional version constraint.
   * @param selected load selection indexed by native pack ID.
   * @return native ID of the selected target.
   * @throws IllegalStateException if the target is absent, ambiguous, or fails the version constraint.
   */
  private static String resolve(String owner, PackRequirement requirement, Map<String, Selected> selected) {
    PackReference ref = requirement.pack();
    List<Map.Entry<String, Selected>> matches = selected.entrySet().stream().filter(entry -> switch (ref) {
      case PackReference.Logical logical -> logical.id().equals(entry.getValue().id);
      case PackReference.Native nativeRef -> nativeRef.id().equals(entry.getKey());
    }).toList();
    if (matches.isEmpty()) {
      throw new IllegalStateException(owner + " requires selected pack " + ref);
    }
    if (matches.size() > 1) {
      throw new IllegalStateException(owner + " requires ambiguous pack " + ref + " matched by " + matches.stream().map(Map.Entry::getKey).toList());
    }
    var match = matches.getFirst();
    String version = match.getValue().version;
    if (requirement.version() != null && (version == null || !Services.PLATFORM.matchesVersion(version, requirement.version()))) {
      throw new IllegalStateException(owner + " requires " + ref + " version " + requirement.version() + ", found " + (version == null ? "no declared version" : version));
    }
    return match.getKey();
  }

  /**
   * Reads the Cobweb section from a {@code pack.mcmeta} document.
   *
   * @param reader open metadata reader.
   * @return Cobweb section, or an empty object when absent.
   */
  private static JsonObject section(Reader reader) {
    return object(JsonParser.parseReader(reader).getAsJsonObject(), "cobweb");
  }

  /**
   * Looks up an object property in metadata.
   *
   * @param source containing metadata object.
   * @param name property name.
   * @return property value, or an empty object when absent.
   */
  private static JsonObject object(JsonObject source, String name) {
    return source.has(name) ? source.getAsJsonObject(name) : new JsonObject();
  }

  /**
   * Reads an optional string without coercing other JSON primitives.
   *
   * @param source containing metadata object.
   * @param name property name.
   * @return string value, or {@code null} when absent.
   * @throws IllegalArgumentException if a present property is not a string.
   */
  @Nullable
  private static String string(JsonObject source, String name) {
    if (!source.has(name)) {
      return null;
    }
    JsonElement value = source.get(name);
    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
      throw new IllegalArgumentException("Expected a string for " + name);
    }
    return value.getAsString();
  }

  /**
   * Parses an optional requirement array into an immutable list.
   *
   * @param source containing metadata object.
   * @param name array property name.
   * @param parser parser for each requirement object.
   * @param <T> parsed requirement type.
   * @return parsed requirements in file order, or an empty list when absent.
   */
  private static <T> List<T> list(JsonObject source, String name, Function<JsonObject, T> parser) {
    List<T> values = new ArrayList<>();
    if (source.has(name)) {
      for (JsonElement entry : source.getAsJsonArray(name)) {
        values.add(parser.apply(entry.getAsJsonObject()));
      }
    }
    return List.copyOf(values);
  }

  /**
   * Parses a pack requirement that identifies its target by exactly one ID.
   *
   * @param entry requirement object.
   * @return parsed pack requirement.
   * @throws IllegalArgumentException if the ID or version constraint is invalid.
   */
  private static PackRequirement requirePack(JsonObject entry) {
    String id = string(entry, "id");
    String nativeId = string(entry, "native_id");
    if ((id == null) == (nativeId == null)) {
      throw new IllegalArgumentException("Supply exactly one of id or native_id for a pack requirement");
    }
    PackReference reference = id == null ? PackReference.nativeId(nativeId) : PackReference.logical(logicalId(id));
    return new PackRequirement(reference, range(entry));
  }

  /**
   * Parses a logical pack ID with an explicit namespace.
   *
   * @param id namespaced identifier in metadata.
   * @return parsed logical identifier.
   * @throws IllegalArgumentException if the namespace separator is missing.
   */
  private static Identifier logicalId(String id) {
    if (!id.contains(":")) {
      throw new IllegalArgumentException("A logical pack ID must include a namespace: " + id);
    }
    return Identifier.parse(id);
  }

  /**
   * Parses a mod requirement and its optional version constraint.
   *
   * @param entry requirement object.
   * @return parsed mod requirement.
   */
  private static ModRequirement requireMod(JsonObject entry) {
    return new ModRequirement(string(entry, "id"), range(entry));
  }

  /**
   * Parses the optional version constraint of a dependency.
   *
   * @param entry requirement object.
   * @return exact version or interval, or {@code null} when absent.
   * @throws IllegalArgumentException if the fields cannot describe a single constraint.
   */
  @Nullable
  private static VersionRange range(JsonObject entry) {
    if (!entry.has("version")) {
      return null;
    }
    var version = entry.getAsJsonObject("version");
    VersionRange.Bound lower = bound(version, "min", "min_exclusive");
    VersionRange.Bound upper = bound(version, "max", "max_exclusive");
    return new VersionRange(string(version, "exact"), lower, upper);
  }

  /**
   * Reads one side of an interval with either an inclusive or an exclusive boundary.
   *
   * @param version version constraint object.
   * @param inclusive property name for an inclusive boundary.
   * @param exclusive property name for an exclusive boundary.
   * @return parsed boundary, or {@code null} when neither property is present.
   * @throws IllegalArgumentException if both properties are present.
   */
  @Nullable
  private static VersionRange.Bound bound(JsonObject version, String inclusive, String exclusive) {
    if (version.has(inclusive) && version.has(exclusive)) {
      throw new IllegalArgumentException("Conflicting version bounds: " + inclusive + " and " + exclusive);
    }
    String lower = string(version, inclusive);
    String upper = string(version, exclusive);
    return lower != null ? new VersionRange.Bound(lower, true) : upper != null ? new VersionRange.Bound(upper, false) : null;
  }

  /**
   * Effective dependency declaration of a selected native pack.
   *
   * @param id logical ID, or {@code null} for a native-only pack.
   * @param version declared pack version, or {@code null} when unknown.
   * @param packs requirements on other packs in the selection.
   * @param mods requirements on loaded mods.
   */
  private record Selected(@Nullable Identifier id, @Nullable String version, List<PackRequirement> packs, List<ModRequirement> mods) {}
}
