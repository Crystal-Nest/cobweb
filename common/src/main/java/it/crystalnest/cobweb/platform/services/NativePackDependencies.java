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
   * Metadata field containing a pack or dependency version.
   */
  private static final String VERSION = "version";

  /**
   * Metadata section containing dependency requirements.
   */
  private static final String REQUIRES = "requires";

  /**
   * Requirement list containing dependencies on packs.
   */
  private static final String PACKS = "packs";

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
    var section = object(document, Constants.MOD_ID);
    section.addProperty("id", declaration.id().toString());
    if (declaration.dependencyVersion() != null) {
      section.addProperty(VERSION, declaration.dependencyVersion());
    }
    if (declaration.requiredPacks() != null || declaration.requiredMods() != null) {
      var requires = object(section, REQUIRES);
      if (declaration.requiredPacks() != null) {
        var list = new JsonArray();
        for (var value : declaration.requiredPacks()) {
          list.add(switch (value.pack()) {
            case PackReference.Logical(var id) -> dependencyJson("id", id.toString(), value.version());
            case PackReference.Native(var id) -> dependencyJson("native_id", id, value.version());
          });
        }
        requires.add(PACKS, list);
      }
      if (declaration.requiredMods() != null) {
        var list = new JsonArray();
        for (var value : declaration.requiredMods()) {
          list.add(dependencyJson("id", value.id(), value.version()));
        }
        requires.add("mods", list);
      }
      section.add(REQUIRES, requires);
    }
    document.add(Constants.MOD_ID, section);
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
      switch (range) {
        case VersionRange.Exact(var value) -> version.addProperty("exact", value);
        case VersionRange.Interval(var minimum, var maximum) -> {
          writeBound(version, "min", "min_exclusive", minimum);
          writeBound(version, "max", "max_exclusive", maximum);
        }
      }
      result.add(VERSION, version);
    }
    return result;
  }

  /**
   * Writes one present endpoint using its inclusive or exclusive metadata field.
   *
   * @param version destination version object.
   * @param inclusive field name for an included endpoint.
   * @param exclusive field name for an excluded endpoint.
   * @param bound endpoint, or {@code null} for an unbounded side.
   */
  private static void writeBound(JsonObject version, String inclusive, String exclusive, @Nullable VersionRange.Bound bound) {
    if (bound != null) {
      String key = switch (bound.inclusion()) {
        case INCLUSIVE -> inclusive;
        case EXCLUSIVE -> exclusive;
      };
      version.addProperty(key, bound.version());
    }
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
  PackInfo info(PackType type, String nativeId, PackResources resources) {
    var declaration = registrations.packDeclaration(type, nativeId);
    if (declaration == null) {
      var source = resources.getRootResource(PackResources.PACK_META);
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
    return new PackInfo(nativeId, declaration.id());
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
      for (PackRequirement requirement : list(object(section, REQUIRES), category, NativePackDependencies::requirePack)) {
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
      if (source != null) {
        try (var reader = new InputStreamReader(source.stream().get(), StandardCharsets.UTF_8)) {
          JsonObject section = section(reader);
          if (section.has("id") || section.has(VERSION)) {
            throw new IllegalArgumentException("Mod root " + Constants.MOD_ID + " metadata cannot declare id or version: " + mod);
          }
          if (section.has(REQUIRES)) {
            String previous = owners.putIfAbsent(source.location(), mod);
            if (previous != null) {
              throw new IllegalArgumentException("Ambiguous mod root pack metadata for " + previous + " and " + mod);
            }
            JsonObject requires = object(section, REQUIRES);
            if (requires.has(PACKS) || requires.has("mods")) {
              throw new IllegalArgumentException("Mod root pack metadata requires data_packs or asset_packs: " + mod);
            }
          }
          sections.put(mod, section);
        } catch (IOException failure) {
          throw new UncheckedIOException("Unable to read mod-root pack metadata of " + mod, failure);
        }
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
    JsonObject metadata;
    try {
      metadata = object(PackHelper.readMetadataFile(resources), Constants.MOD_ID);
    } catch (IOException failure) {
      throw new UncheckedIOException("Unable to read pack metadata of " + resources.packId(), failure);
    }
    String nativeId = resources.packId();
    var declaration = registrations.packDeclaration(type, nativeId);
    if (!(declaration == null && !nativeId.startsWith(PackHelper.FILE_PACK_PREFIX) && modSections.containsValue(metadata))) {
      return readDeclaration(nativeId, metadata, declaration);
    }
    return new Selected(null, null, List.of(), List.of());
  }

  /**
   * Resolves a pack declaration in validation order and applies Java overrides before decoding inherited requirement lists.
   *
   * @param nativeId native pack identity used in diagnostics.
   * @param metadata pack dependency metadata.
   * @param declaration registered Java declaration, or {@code null} for an unmanaged pack.
   * @return effective identity, version and requirements.
   * @throws IllegalArgumentException if metadata is invalid or conflicts with the registered identity.
   */
  private static Selected readDeclaration(String nativeId, JsonObject metadata, @Nullable PackDeclaration declaration) {
    String version = readPackVersion(nativeId, metadata);
    JsonObject requires = readPackRequirements(nativeId, metadata);
    Identifier id = resolvePackId(nativeId, metadata, declaration);
    if (!(declaration == null || declaration.dependencyVersion() == null)) {
      if (!(version == null || version.equals(declaration.dependencyVersion()))) {
        Constants.LOGGER.warn("Using the Java dependency version of {} instead of {}", nativeId, version);
      }
      version = declaration.dependencyVersion();
    }
    List<PackRequirement> packs = declaration == null ? null : declaration.requiredPacks();
    List<ModRequirement> mods = declaration == null ? null : declaration.requiredMods();
    return new Selected(id, version, packs == null ? list(requires, PACKS, NativePackDependencies::requirePack) : packs,
      mods == null ? list(requires, "mods", NativePackDependencies::requireMod) : mods);
  }

  /**
   * Reads the optional file version before Java overrides are applied.
   *
   * @param nativeId native pack identity used in diagnostics.
   * @param metadata pack dependency metadata.
   * @return file version, or {@code null} when absent.
   * @throws IllegalArgumentException if the version is not a string or is blank.
   */
  @Nullable
  private static String readPackVersion(String nativeId, JsonObject metadata) {
    String version = string(metadata, VERSION);
    if (version == null || !version.isBlank()) {
      return version;
    }
    throw new IllegalArgumentException("Blank pack version for " + nativeId);
  }

  /**
   * Reads the pack requirement container without decoding lists that Java may replace.
   *
   * @param nativeId native pack identity used in diagnostics.
   * @param metadata pack dependency metadata.
   * @return requirement container, or an empty object when absent.
   * @throws IllegalArgumentException if the container uses mod-root-only requirement fields.
   */
  private static JsonObject readPackRequirements(String nativeId, JsonObject metadata) {
    JsonObject requires = object(metadata, REQUIRES);
    if (!(requires.has("data_packs") || requires.has("asset_packs"))) {
      return requires;
    }
    throw new IllegalArgumentException("Pack dependencies use packs or mods: " + nativeId);
  }

  /**
   * Resolves the file identity against a registered Java identity.
   *
   * @param nativeId native pack identity used in diagnostics.
   * @param metadata pack dependency metadata.
   * @param declaration registered Java declaration, or {@code null} for an unmanaged pack.
   * @return registered identity or file identity, or {@code null} when neither exists.
   * @throws IllegalArgumentException if the file identity is invalid or changes the registered identity.
   */
  @Nullable
  private static Identifier resolvePackId(String nativeId, JsonObject metadata, @Nullable PackDeclaration declaration) {
    String value = string(metadata, "id");
    Identifier id = value == null ? null : logicalId(value);
    if (declaration != null) {
      if (id == null || declaration.id().equals(id)) {
        return declaration.id();
      }
      throw new IllegalArgumentException("Metadata cannot change the logical ID of " + nativeId + " to " + id);
    }
    return id;
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
      case PackReference.Logical(var id) -> id.equals(entry.getValue().id);
      case PackReference.Native(var id) -> id.equals(entry.getKey());
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
    return object(JsonParser.parseReader(reader).getAsJsonObject(), Constants.MOD_ID);
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
    if (source.has(name)) {
      JsonElement value = source.get(name);
      if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
        return value.getAsString();
      }
      throw new IllegalArgumentException("Expected a string for " + name);
    }
    return null;
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
    if (entry.has(VERSION)) {
      var version = entry.getAsJsonObject(VERSION);
      VersionRange.Bound minimum = bound(version, "min", "min_exclusive");
      VersionRange.Bound maximum = bound(version, "max", "max_exclusive");
      String exact = string(version, "exact");
      if (exact == null || minimum == null && maximum == null) {
        return exact == null ? VersionRange.interval(minimum, maximum) : VersionRange.exact(exact);
      }
      throw new IllegalArgumentException("An exact version cannot also declare interval bounds");
    }
    return null;
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
    if (!(version.has(inclusive) && version.has(exclusive))) {
      String included = string(version, inclusive);
      String excluded = string(version, exclusive);
      if (included == null) {
        return excluded == null ? null : VersionRange.Bound.exclusive(excluded);
      }
      return VersionRange.Bound.inclusive(included);
    }
    throw new IllegalArgumentException("Conflicting version bounds: " + inclusive + " and " + exclusive);
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
