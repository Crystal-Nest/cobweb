package it.crystalnest.cobweb.api.pack.dependency;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.crystalnest.cobweb.Constants;
import it.crystalnest.cobweb.api.pack.query.PackInfo;
import it.crystalnest.cobweb.api.pack.query.PackReference;
import it.crystalnest.cobweb.platform.Services;
import it.crystalnest.cobweb.platform.services.PackHelper.PackDeclaration;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackMetadataResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import org.jetbrains.annotations.ApiStatus;
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
import java.util.Objects;
import java.util.function.Function;

/**
 * Resolves the dependency metadata of the native pack selection for one resource load.
 */
@ApiStatus.Internal
public final class NativePackDependencies {
  private static final Map<ModRequirementKey, List<PackRequirement>> modOverrides = new LinkedHashMap<>();

  private static boolean declarationsClosed;

  private NativePackDependencies() {}

  /**
   * Writes the effective registered identity and Java dependency overrides into an inspection export.
   *
   * @param document detached pack metadata.
   * @param declaration registered pack declaration.
   */
  public static void exportMetadata(JsonObject document, PackDeclaration declaration) {
    var section = object(document, "cobweb");
    section.addProperty("id", declaration.id().toString());
    var settings = declaration.dependencies();
    if (settings.version() != null) {
      section.addProperty("version", settings.version());
    }
    if (settings.packs() != null || settings.mods() != null) {
      var requires = object(section, "requires");
      if (settings.packs() != null) {
        var list = new JsonArray();
        for (var value : settings.packs()) {
          var ref = value.pack();
          list.add(dependencyJson(ref.logicalId() == null ? "native_id" : "id", ref.logicalId() == null ? ref.nativeId() : ref.logicalId().toString(), value.version()));
        }
        requires.add("packs", list);
      }
      if (settings.mods() != null) {
        var list = new JsonArray();
        for (var value : settings.mods()) {
          list.add(dependencyJson("id", value.id(), value.version()));
        }
        requires.add("mods", list);
      }
      section.add("requires", requires);
    }
    document.add("cobweb", section);
  }

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
      if (range.allowPrerelease()) {
        version.addProperty("allow_prerelease", true);
      }
      result.add("version", version);
    }
    return result;
  }

  /**
   * Reads identity for a native pack observation.
   *
   * @param type native pack type.
   * @param resources opened pack resources.
   * @return pack identity, including a logical identity when available.
   */
  public static PackInfo info(PackType type, PackResources resources) {
    return info(type, resources.packId(), resources);
  }

  /**
   * Reads identity from a pack's primary metadata.
   *
   * @param type native pack type.
   * @param nativeId native repository identity.
   * @param resources opened primary metadata.
   * @return pack identity, including a logical identity when available.
   */
  public static PackInfo info(PackType type, String nativeId, PackMetadataResources resources) {
    var declaration = Services.PACK.packDeclaration(type, nativeId);
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
   * Records one explicit Java override for a loaded mod's requirement list.
   *
   * @param modId declaring mod's loader ID.
   * @param type native pack type.
   * @param values required packs.
   */
  public static synchronized void declareModRequirements(String modId, PackType type, List<PackRequirement> values) {
    if (declarationsClosed) {
      throw new IllegalStateException("Mod pack requirements have closed: " + modId);
    }
    if (!Services.PLATFORM.isModLoaded(modId)) {
      throw new IllegalArgumentException("Unknown declaring mod: " + modId);
    }
    if (modOverrides.putIfAbsent(new ModRequirementKey(modId, Objects.requireNonNull(type)), List.copyOf(values)) != null) {
      throw new IllegalStateException("Mod pack requirements already declared: " + modId + " for " + type);
    }
  }

  /**
   * Closes the declaration window for mod-owned requirements.
   */
  public static synchronized void closeDeclarations() {
    declarationsClosed = true;
  }

  /**
   * Validates selected pack and mod-root requirements and resolves each pack's selected dependencies.
   *
   * @param type native pack side being loaded.
   * @param packs selected native stack in resource priority order.
   * @return selected dependency targets indexed by the requiring native pack ID.
   */
  public static Map<String, List<String>> validate(PackType type, List<PackResources> packs) {
    closeDeclarations();
    return validate(type, packs, readModSections(), false);
  }

  /**
   * Validates the dependencies declared by asset packs selected for server delivery.
   * Rejects client mod requirements because delivery also supports clients without Cobweb.
   *
   * @param packs selected packs in publication priority order.
   */
  public static void validatePublication(List<PackResources> packs) {
    validate(PackType.CLIENT_RESOURCES, packs, Map.of(), true);
  }

  private static Map<String, List<String>> validate(PackType type, List<PackResources> packs, Map<String, JsonObject> modSections, boolean publication) {
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
      var override = modOverrides.get(new ModRequirementKey(mod, type));
      List<PackRequirement> requirements = override != null ? override : list(object(section, "requires"), category, NativePackDependencies::requirePack);
      for (PackRequirement requirement : requirements) {
        resolve("mod " + mod, requirement, selected);
      }
    });
    return Map.copyOf(result);
  }

  private static Map<String, JsonObject> readModSections() {
    Map<String, JsonObject> sections = new LinkedHashMap<>();
    Map<URI, String> owners = new LinkedHashMap<>();
    for (String mod : Services.PLATFORM.loadedModIds()) {
      var source = Services.PLATFORM.modRootPackMetadata(mod);
      if (source == null) {
        sections.put(mod, new JsonObject());
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

  private static Selected discover(PackType type, PackResources resources, Map<String, JsonObject> modSections) {
    JsonObject metadata = new JsonObject();
    var root = resources.getRootResource("pack.mcmeta");
    if (root != null) {
      try (var reader = new InputStreamReader(root.get(), StandardCharsets.UTF_8)) {
        metadata = section(reader);
      } catch (IOException failure) {
        throw new UncheckedIOException("Unable to read pack metadata of " + resources.packId(), failure);
      }
    }
    var java = Services.PACK.packDeclaration(type, resources.packId());
    if (java == null && !resources.packId().startsWith("file/") && modSections.containsValue(metadata)) {
      return new Selected(null, null, List.of(), List.of());
    }
    String id = string(metadata, "id");
    Identifier logicalId = id == null ? null : logicalId(id);
    String version = string(metadata, "version");
    if (version != null) {
      PackVersions.validate(version);
    }
    JsonObject requires = object(metadata, "requires");
    if (requires.has("data_packs") || requires.has("asset_packs")) {
      throw new IllegalArgumentException("Pack dependencies use packs or mods: " + resources.packId());
    }
    List<PackRequirement> packs = java == null ? null : java.dependencies().packs();
    List<ModRequirement> mods = java == null ? null : java.dependencies().mods();
    if (java != null) {
      if (logicalId != null && !java.id().equals(logicalId)) {
        throw new IllegalArgumentException("Metadata cannot change the logical ID of " + resources.packId() + " to " + logicalId);
      }
      logicalId = java.id();
      if (java.dependencies().version() != null) {
        if (version != null && !version.equals(java.dependencies().version())) {
          Constants.LOGGER.warn("Using the Java dependency version of {} instead of {}", resources.packId(), version);
        }
        version = java.dependencies().version();
      }
    }
    if (version != null) {
      PackVersions.validate(version);
    }
    return new Selected(logicalId, version, packs == null ? list(requires, "packs", NativePackDependencies::requirePack) : packs,
      mods == null ? list(requires, "mods", NativePackDependencies::requireMod) : mods);
  }

  private static String resolve(String owner, PackRequirement requirement, Map<String, Selected> selected) {
    PackReference ref = requirement.pack();
    List<Map.Entry<String, Selected>> matches = selected.entrySet().stream().filter(entry -> ref.nativeId() != null
      ? ref.nativeId().equals(entry.getKey()) : ref.logicalId().equals(entry.getValue().id)).toList();
    if (matches.isEmpty()) {
      throw new IllegalStateException(owner + " requires selected pack " + ref);
    }
    if (matches.size() > 1) {
      throw new IllegalStateException(owner + " requires ambiguous pack " + ref + " matched by " + matches.stream().map(Map.Entry::getKey).toList());
    }
    var match = matches.getFirst();
    if (requirement.version() != null && !PackVersions.matches(match.getValue().version, requirement.version())) {
      throw new IllegalStateException(owner + " requires " + ref + " version " + requirement.version() + ", found " + match.getValue().version);
    }
    return match.getKey();
  }

  private static JsonObject section(Reader reader) {
    return object(JsonParser.parseReader(reader).getAsJsonObject(), "cobweb");
  }

  private static JsonObject object(JsonObject source, String name) {
    return source.has(name) ? source.getAsJsonObject(name) : new JsonObject();
  }

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

  private static <T> List<T> list(JsonObject source, String name, Function<JsonObject, T> parser) {
    List<T> values = new ArrayList<>();
    if (source.has(name)) {
      for (JsonElement entry : source.getAsJsonArray(name)) {
        values.add(parser.apply(entry.getAsJsonObject()));
      }
    }
    return List.copyOf(values);
  }

  private static PackRequirement requirePack(JsonObject entry) {
    String id = string(entry, "id");
    String nativeId = string(entry, "native_id");
    return new PackRequirement(new PackReference(id == null ? null : logicalId(id), nativeId), range(entry));
  }

  private static Identifier logicalId(String id) {
    if (!id.contains(":")) {
      throw new IllegalArgumentException("A logical pack ID must include a namespace: " + id);
    }
    return Identifier.parse(id);
  }

  private static ModRequirement requireMod(JsonObject entry) {
    return new ModRequirement(string(entry, "id"), range(entry));
  }

  @Nullable
  private static VersionRange range(JsonObject entry) {
    if (!entry.has("version")) {
      return null;
    }
    var version = entry.getAsJsonObject("version");
    VersionRange.Bound lower = bound(version, "min", "min_exclusive");
    VersionRange.Bound upper = bound(version, "max", "max_exclusive");
    boolean allow = false;
    if (version.has("allow_prerelease")) {
      var flag = version.get("allow_prerelease");
      if (!flag.isJsonPrimitive() || !flag.getAsJsonPrimitive().isBoolean()) {
        throw new IllegalArgumentException("allow_prerelease must be a boolean");
      }
      allow = flag.getAsBoolean();
    }
    return new VersionRange(string(version, "exact"), lower, upper, allow);
  }

  @Nullable
  private static VersionRange.Bound bound(JsonObject version, String inclusive, String exclusive) {
    if (version.has(inclusive) && version.has(exclusive)) {
      throw new IllegalArgumentException("Conflicting version bounds: " + inclusive + " and " + exclusive);
    }
    String lower = string(version, inclusive);
    String upper = string(version, exclusive);
    return lower != null ? new VersionRange.Bound(lower, true) : upper != null ? new VersionRange.Bound(upper, false) : null;
  }

  private record Selected(@Nullable Identifier id, @Nullable String version, List<PackRequirement> packs, List<ModRequirement> mods) {}

  private record ModRequirementKey(String mod, PackType type) {}
}
