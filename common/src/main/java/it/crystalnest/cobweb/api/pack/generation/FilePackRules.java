package it.crystalnest.cobweb.api.pack.generation;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import it.crystalnest.cobweb.api.pack.generation.lifecycle.Phase;
import it.crystalnest.cobweb.api.pack.generation.lifecycle.Stage;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.util.StrictJsonParser;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reads an owned rule manifest and executes its file operations against one stage snapshot.
 *
 * @param rules file rules in manifest order.
 * @param before logical IDs of preferred generation successors.
 * @param after logical IDs of preferred generation predecessors.
 */
record FilePackRules(List<Rule> rules, List<Identifier> before, List<Identifier> after) {
  /**
   * Schema key selecting the destination-condition mismatch policy.
   */
  private static final String ON_MISMATCH = "on_mismatch";

  /**
   * Rule manifest with no callbacks or ordering declarations.
   */
  static final FilePackRules EMPTY = new FilePackRules(List.of(), List.of(), List.of());

  /**
   * Maximum readable manifest length in bytes.
   */
  private static final int MAX_MANIFEST = 1024 * 1024;

  /**
   * Maximum length of JSON read or produced by a patch rule.
   */
  private static final int MAX_JSON = 8 * 1024 * 1024;

  /**
   * Maximum number of rules in one section or edits in one patch.
   */
  private static final int MAX_RULES = 1024;

  /**
   * Maximum resource size accepted by a copy rule.
   */
  private static final long MAX_COPY = 64L * 1024 * 1024;

  /**
   * Reads the rule section owned by this pack opening.
   *
   * @param type native pack type to read.
   * @param pack owning pack opening.
   * @return rules for the requested type, or {@link #EMPTY} when absent.
   * @throws IllegalArgumentException if the owned manifest cannot be read or is invalid.
   */
  static FilePackRules read(PackType type, PackResources pack) {
    var resource = pack.getRootResource("cobweb", "rules.json");
    if (resource == null) {
      return EMPTY;
    }
    try (var input = resource.get()) {
      var root = readJson(input, MAX_MANIFEST).getAsJsonObject();
      var format = root.getAsJsonPrimitive("format");
      if (format == null || !format.isNumber() || format.getAsBigDecimal().intValueExact() != 1) {
        throw new IllegalArgumentException("Expected file rule format 1");
      }
      String side = type == PackType.CLIENT_RESOURCES ? "assets" : "data";
      if (!root.has(side)) {
        return EMPTY;
      }
      var section = root.getAsJsonObject(side);
      var entries = section.getAsJsonArray("rules");
      var rules = new ArrayList<Rule>();
      if (entries != null) {
        if (entries.size() > MAX_RULES) {
          throw new IllegalArgumentException("Too many file rules");
        }
        for (int index = 0; index < entries.size(); index++) {
          rules.add(readRule(entries.get(index), type, index));
        }
      }
      return new FilePackRules(List.copyOf(rules), ids(section, "before"), ids(section, "after"));
    } catch (IOException | RuntimeException failure) {
      throw new IllegalArgumentException(pack.packId() + "/cobweb/rules.json: " + failure.getMessage(), failure);
    }
  }

  /**
   * Decodes one rule and adds its manifest index to invalid-rule diagnostics.
   *
   * @param entry rule value from the manifest.
   * @param type native pack type.
   * @param index declaration index in the manifest.
   * @return validated rule.
   * @throws IllegalArgumentException if the rule is invalid.
   */
  private static Rule readRule(JsonElement entry, PackType type, int index) {
    try {
      return Rule.read(entry.getAsJsonObject(), type, index);
    } catch (RuntimeException failure) {
      throw new IllegalArgumentException("Rule " + index + ": " + failure.getMessage(), failure);
    }
  }

  /**
   * Collects namespaces introduced by rule outputs.
   *
   * @return immutable set of output namespaces.
   */
  Set<String> namespaces() {
    return rules.stream().map(rule -> rule.to.getNamespace()).collect(Collectors.toUnmodifiableSet());
  }

  /**
   * Reads an optional list of logical pack IDs.
   *
   * @param object manifest section.
   * @param key list property name.
   * @return immutable list of pack IDs, or an empty list when the property is absent.
   */
  private static List<Identifier> ids(JsonObject object, String key) {
    if (!object.has(key)) {
      return List.of();
    }
    var result = new ArrayList<Identifier>();
    for (var id : object.getAsJsonArray(key)) {
      result.add(Identifier.parse(string(id)));
    }
    return List.copyOf(result);
  }

  /**
   * Reads a required JSON string.
   *
   * @param value candidate JSON value.
   * @return string value.
   * @throws IllegalArgumentException if the value is absent or not a string.
   */
  private static String string(@Nullable JsonElement value) {
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
      throw new IllegalArgumentException("Expected a string");
    }
    return value.getAsString();
  }

  /**
   * Reads an optional string property.
   *
   * @param object manifest object.
   * @param key property name.
   * @param fallback default string.
   * @return property value, or the fallback when absent.
   */
  private static String string(JsonObject object, String key, String fallback) {
    return object.has(key) ? string(object.get(key)) : fallback;
  }

  /**
   * Parses a bounded UTF-8 JSON document.
   *
   * @param input stream to read.
   * @param limit maximum number of JSON bytes.
   * @return parsed JSON value.
   * @throws IOException if the underlying I/O operation fails.
   */
  private static JsonElement readJson(InputStream input, int limit) throws IOException {
    var bytes = input.readNBytes(limit + 1);
    if (bytes.length > limit) {
      throw new IOException("JSON file exceeds " + limit + " bytes");
    }
    return StrictJsonParser.parse(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString());
  }

  /**
   * Identifies a file rule's native resource operation.
   */
  enum Operation {
    /**
     * Copies bytes and optionally the effective file-backed sidecar.
     */
    COPY,
    /**
     * Applies ordered edits to a JSON document.
     */
    PATCH_JSON,
    /**
     * Remaps source colors against a palette, with an optional mask.
     */
    RECOLOR_TEXTURE,
    /**
     * Alpha-blends a second image over the source.
     */
    OVERLAY_TEXTURE,
    /**
     * Writes inline JSON or text, optionally checking destination presence first.
     */
    WRITE
  }

  /**
   * Describes one validated operation and its scheduling information.
   *
   * @param payload operation and its inputs.
   * @param to destination resource identifier.
   * @param stage stage in the selected phase.
   * @param phase requested phase, or {@code null} for inference.
   * @param skipMissing whether to skip missing source resources.
   * @param index declaration index in the manifest.
   * @param fingerprint hash of the parsed rule definition.
   */
  record Rule(Payload payload, Identifier to, Stage stage, @Nullable Phase phase, boolean skipMissing, int index, String fingerprint) {
    /**
     * Reads an explicitly namespaced file identifier and validates its path.
     *
     * @param object rule object.
     * @param key name of the file property.
     * @return namespaced file identifier.
     * @throws IllegalArgumentException if the identifier is absent or invalid.
     */
    private static Identifier resource(JsonObject object, String key) {
      String text = string(object.get(key));
      if (text.indexOf(':') < 1) {
        throw new IllegalArgumentException("Expected a namespaced resource for " + key);
      }
      Identifier id = Identifier.parse(text);
      try {
        PackPaths.resourcePath(PackType.CLIENT_RESOURCES, id);
      } catch (IOException failure) {
        throw new IllegalArgumentException(failure.getMessage(), failure);
      }
      return id;
    }

    /**
     * Parses and validates a rule, then fingerprints its definition.
     *
     * @param object rule object.
     * @param type native pack type.
     * @param index declaration index.
     * @return validated rule ready for phase selection.
     */
    static Rule read(JsonObject object, PackType type, int index) {
      Operation operation = Operation.valueOf(string(object.get("op")).toUpperCase(Locale.ROOT));
      if (operation != Operation.WRITE && (object.has("if_destination") || object.has(ON_MISMATCH))) {
        throw new IllegalArgumentException("Destination conditions require a write rule");
      }
      boolean withMetadata = metadataFlag(object, operation);
      Identifier from = operation == Operation.WRITE ? null : resource(object, "from");
      Identifier to = resource(object, "to");
      Payload payload = switch (operation) {
        case COPY -> new Copy(from, withMetadata);
        case PATCH_JSON -> readPatch(object, from, to);
        case RECOLOR_TEXTURE, OVERLAY_TEXTURE -> readTexture(object, type, operation, from, to);
        case WRITE -> InlineWrite.read(object);
      };
      var digest = CacheObservations.digest();
      CacheObservations.text(digest, object.toString());
      var stage = Stage.valueOf(string(object, "stage", "transformation").toUpperCase(Locale.ROOT));
      var phase = object.has("phase") ? Phase.valueOf(string(object.get("phase")).toUpperCase(Locale.ROOT)) : null;
      boolean skip = skipMissing(object);
      return new Rule(payload, to, stage, phase, skip, index, CacheObservations.hex(digest));
    }

    /**
     * Reads the policy for an absent source file.
     *
     * @param object rule object.
     * @return whether a missing source skips the rule.
     * @throws IllegalArgumentException if the policy is unknown.
     */
    private static boolean skipMissing(JsonObject object) {
      return switch (string(object, "on_missing", "fail")) {
        case "skip" -> true;
        case "fail" -> false;
        default -> throw new IllegalArgumentException("Unknown on_missing policy");
      };
    }

    /**
     * Reads the optional sidecar-copy flag and rejects it for other operations.
     *
     * @param object rule object.
     * @param operation selected file operation.
     * @return whether the copy includes effective metadata.
     * @throws IllegalArgumentException if the flag is invalid for this operation.
     */
    private static boolean metadataFlag(JsonObject object, Operation operation) {
      if (!object.has("with_metadata")) {
        return false;
      }
      if (operation != Operation.COPY) {
        throw new IllegalArgumentException("with_metadata requires a copy rule");
      }
      var value = object.get("with_metadata");
      if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
        throw new IllegalArgumentException("Expected a boolean for with_metadata");
      }
      return value.getAsBoolean();
    }

    /**
     * Decodes ordered JSON edits after checking their source and destination formats.
     *
     * @param object rule object.
     * @param from source JSON file.
     * @param to destination JSON file.
     * @return complete patch payload.
     * @throws IllegalArgumentException if the paths, edits or edit count are invalid.
     */
    private static Patch readPatch(JsonObject object, Identifier from, Identifier to) {
      if (!from.getPath().endsWith(".json") || !to.getPath().endsWith(".json")) {
        throw new IllegalArgumentException("patch_json requires JSON file paths");
      }
      var edits = new ArrayList<JsonEdits.Edit>();
      for (var entry : object.getAsJsonArray("edits")) {
        var edit = entry.getAsJsonObject();
        var editOp = JsonEdits.Operation.valueOf(string(edit.get("op")).toUpperCase(Locale.ROOT));
        var value = edit.get("value");
        if (value == null && editOp != JsonEdits.Operation.REMOVE) {
          throw new IllegalArgumentException("JSON edit requires a value");
        }
        if (edits.size() >= MAX_RULES) {
          throw new IllegalArgumentException("Too many JSON edits");
        }
        edits.add(new JsonEdits.Edit(editOp, JsonEdits.pointer(string(edit.get("path"))), value == null ? JsonNull.INSTANCE : value));
      }
      return new Patch(from, List.copyOf(edits));
    }

    /**
     * Decodes texture inputs and checks that every file is a client PNG under the texture directory.
     *
     * @param object rule object.
     * @param type native pack type.
     * @param operation selected texture operation.
     * @param from primary source texture.
     * @param to destination texture.
     * @return complete palette-remap or overlay payload.
     * @throws IllegalArgumentException if the resource side or a texture path is invalid.
     */
    private static Payload readTexture(JsonObject object, PackType type, Operation operation, Identifier from, Identifier to) {
      if (type != PackType.CLIENT_RESOURCES) {
        throw new IllegalArgumentException("Texture rules require client assets");
      }
      Identifier secondary = resource(object, operation == Operation.RECOLOR_TEXTURE ? "palette_from" : "overlay");
      Identifier mask = operation == Operation.RECOLOR_TEXTURE && object.has("mask") ? resource(object, "mask") : null;
      for (Identifier id : new Identifier[]{from, to, secondary, mask}) {
        if (id != null && (!id.getPath().startsWith("textures/") || !id.getPath().endsWith(".png") || id.getPath().length() <= 13)) {
          throw new IllegalArgumentException("Expected a PNG under textures/: " + id);
        }
      }
      return operation == Operation.RECOLOR_TEXTURE ? new Recolor(from, secondary, mask) : new Overlay(from, secondary);
    }

    /**
     * Returns the primary source used to infer this rule's constraints.
     *
     * @return primary source identifier, or {@code null} for an inline write.
     */
    @Nullable
    Identifier from() {
      var inputs = payload.inputs();
      return inputs.isEmpty() ? null : inputs.getFirst();
    }

    /**
     * Resolves the inputs and submits the completed file operation.
     *
     * @param context active callback context.
     * @param output destination for completed contributions.
     * @throws IOException if an input is missing and required, or the file operation fails.
     */
    void generate(GenerationContext context, GenerationOutput output) throws IOException {
      Map<Identifier, GenerationResources.File> inputs = new HashMap<>();
      for (Identifier id : payload.inputs()) {
        var source = context.resources().get(id);
        if (source.isEmpty()) {
          if (skipMissing) {
            return;
          }
          throw new IOException("Missing rule input " + id);
        }
        inputs.put(id, source.get());
      }
      switch (payload) {
        case Copy(var from, boolean withMetadata) -> output.copy(to, inputs.get(from), withMetadata, MAX_COPY);
        case Patch(var from, var edits) -> {
          try (var input = inputs.get(from).open(MAX_JSON)) {
            byte[] result = PackIO.json(JsonEdits.apply(readJson(input, MAX_JSON), edits));
            if (result.length > MAX_JSON) {
              throw new IOException("Patched JSON exceeds " + MAX_JSON + " bytes");
            }
            output.write(to, stream -> stream.write(result));
          }
        }
        case Recolor(var from, var palette, var mask) -> TextureRules.recolor(output, to, inputs.get(from), inputs.get(palette), inputs.get(mask));
        case Overlay(var from, var overlay) -> TextureRules.overlay(output, to, inputs.get(from), inputs.get(overlay));
        case InlineWrite inline -> inline.generate(context, output, to);
      }
    }
  }

  /**
   * Describes the inputs and parameters of a file operation.
   */
  private sealed interface Payload {
    /**
     * Returns the resource files this operation must read.
     *
     * @return source file identifiers in resolution order.
     */
    List<Identifier> inputs();
  }

  /**
   * Describes a file copy and whether it includes the effective sidecar.
   *
   * @param from source file identifier.
   * @param withMetadata whether to include its effective metadata.
   */
  private record Copy(Identifier from, boolean withMetadata) implements Payload {
    @Override
    public List<Identifier> inputs() {
      return List.of(from);
    }
  }

  /**
   * Describes edits applied in order to a source JSON file.
   *
   * @param from source JSON file identifier.
   * @param edits ordered JSON edits.
   */
  private record Patch(Identifier from, List<JsonEdits.Edit> edits) implements Payload {
    @Override
    public List<Identifier> inputs() {
      return List.of(from);
    }
  }

  /**
   * Describes a palette remapping operation.
   *
   * @param from source texture file identifier.
   * @param palette texture that supplies the palette.
   * @param mask optional texture controlling which pixels are recolored.
   */
  private record Recolor(Identifier from, Identifier palette, @Nullable Identifier mask) implements Payload {
    @Override
    public List<Identifier> inputs() {
      return mask == null ? List.of(from, palette) : List.of(from, palette, mask);
    }
  }

  /**
   * Describes a source-over image blend.
   *
   * @param from base texture file identifier.
   * @param overlay texture drawn over the base.
   */
  private record Overlay(Identifier from, Identifier overlay) implements Payload {
    @Override
    public List<Identifier> inputs() {
      return List.of(from, overlay);
    }
  }

  /**
   * Selects the required destination state in the stable stage view.
   */
  private enum Destination {
    /**
     * Writes only when the destination resolves to a resource.
     */
    PRESENT,
    /**
     * Writes only when the destination is missing.
     */
    ABSENT
  }

  /**
   * Describes a complete inline resource with an optional presence condition.
   *
   * @param contents resource contents as JSON or plain text.
   * @param destination required destination state, or {@code null} for unconditional output.
   * @param failOnMismatch whether a mismatched condition fails the load.
   */
  private record InlineWrite(String contents, @Nullable Destination destination, boolean failOnMismatch) implements Payload {
    @Override
    public List<Identifier> inputs() {
      return List.of();
    }

    /**
     * Validates inline contents and the destination condition.
     *
     * @param object rule object.
     * @return complete inline write payload.
     * @throws IllegalArgumentException if contents or conditions are invalid.
     */
    private static InlineWrite read(JsonObject object) {
      if (object.has("from") || object.has("on_missing")) {
        throw new IllegalArgumentException("A write rule supplies its contents inline and cannot specify from or on_missing");
      }
      var json = object.get("json");
      var text = object.get("text");
      if ((json == null) == (text == null)) {
        throw new IllegalArgumentException("A write rule requires exactly one of json or text");
      }
      var condition = object.get("if_destination");
      Destination destination = condition == null ? null : switch (string(condition)) {
        case "present" -> Destination.PRESENT;
        case "absent" -> Destination.ABSENT;
        default -> throw new IllegalArgumentException("Expected present or absent for if_destination");
      };
      if (destination == null && object.has(ON_MISMATCH)) {
        throw new IllegalArgumentException("on_mismatch requires if_destination");
      }
      boolean failOnMismatch = switch (string(object, ON_MISMATCH, "skip")) {
        case "skip" -> false;
        case "fail" -> true;
        default -> throw new IllegalArgumentException("Unknown on_mismatch policy");
      };
      return new InlineWrite(json == null ? string(text) : json.toString(), destination, failOnMismatch);
    }

    /**
     * Writes complete inline contents when the destination condition is met.
     *
     * @param context active callback context.
     * @param output destination for completed contributions.
     * @param to destination resource identifier.
     * @throws IOException if a mismatched destination is configured to fail.
     */
    private void generate(GenerationContext context, GenerationOutput output, Identifier to) throws IOException {
      if (destination != null && context.resources().get(to).isPresent() != (destination == Destination.PRESENT)) {
        if (failOnMismatch) {
          throw new IOException("Destination condition failed for " + to + ", expected " + destination.name().toLowerCase(Locale.ROOT));
        }
        return;
      }
      output.putText(to, contents);
    }
  }
}
