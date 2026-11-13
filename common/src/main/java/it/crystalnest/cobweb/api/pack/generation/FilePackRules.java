package it.crystalnest.cobweb.api.pack.generation;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import it.crystalnest.cobweb.api.pack.generation.lifecycle.Phase;
import it.crystalnest.cobweb.api.pack.generation.lifecycle.Stage;
import it.crystalnest.cobweb.impl.pack.client.TextureRules;
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
 */
record FilePackRules(List<Rule> rules, List<Identifier> before, List<Identifier> after) {
  static final FilePackRules EMPTY = new FilePackRules(List.of(), List.of(), List.of());

  private static final int MAX_MANIFEST = 1024 * 1024;

  private static final int MAX_JSON = 8 * 1024 * 1024;

  private static final int MAX_RULES = 1024;

  private static final long MAX_COPY = 64L * 1024 * 1024;

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
          try {
            rules.add(Rule.read(entries.get(index).getAsJsonObject(), type, index));
          } catch (RuntimeException failure) {
            throw new IllegalArgumentException("Rule " + index + ": " + failure.getMessage(), failure);
          }
        }
      }
      return new FilePackRules(List.copyOf(rules), ids(section, "before"), ids(section, "after"));
    } catch (IOException | RuntimeException failure) {
      throw new IllegalArgumentException(pack.packId() + "/cobweb/rules.json: " + failure.getMessage(), failure);
    }
  }

  Set<String> namespaces() {
    return rules.stream().map(rule -> rule.to.getNamespace()).collect(Collectors.toUnmodifiableSet());
  }

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

  private static String string(@Nullable JsonElement value) {
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
      throw new IllegalArgumentException("Expected a string");
    }
    return value.getAsString();
  }

  private static String string(JsonObject object, String key, String fallback) {
    return object.has(key) ? string(object.get(key)) : fallback;
  }

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

  private static JsonElement readJson(InputStream input, int limit) throws IOException {
    var bytes = input.readNBytes(limit + 1);
    if (bytes.length > limit) {
      throw new IOException("JSON file exceeds " + limit + " bytes");
    }
    return StrictJsonParser.parse(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString());
  }

  enum Operation {
    COPY,
    PATCH_JSON,
    RECOLOR_TEXTURE,
    OVERLAY_TEXTURE,
    WRITE
  }

  record Rule(Payload payload, Identifier to, Stage stage, @Nullable Phase phase, boolean skipMissing, int index, String fingerprint) {
    static Rule read(JsonObject object, PackType type, int index) {
      Operation operation = Operation.valueOf(string(object.get("op")).toUpperCase(Locale.ROOT));
      if (operation != Operation.WRITE && (object.has("if_destination") || object.has("on_mismatch"))) {
        throw new IllegalArgumentException("Destination conditions require a write rule");
      }
      Identifier from = operation == Operation.WRITE ? null : resource(object, "from");
      Identifier to = resource(object, "to");
      var stage = Stage.valueOf(string(object, "stage", "transformation").toUpperCase(Locale.ROOT));
      var phase = object.has("phase") ? Phase.valueOf(string(object.get("phase")).toUpperCase(Locale.ROOT)) : null;
      boolean skip = switch (string(object, "on_missing", "fail")) {
        case "skip" -> true;
        case "fail" -> false;
        default -> throw new IllegalArgumentException("Unknown on_missing policy");
      };
      Identifier secondary = null;
      Identifier mask = null;
      var edits = new ArrayList<JsonEdits.Edit>();
      InlineWrite inline = null;
      boolean withMetadata = false;
      if (object.has("with_metadata")) {
        if (operation != Operation.COPY) {
          throw new IllegalArgumentException("with_metadata requires a copy rule");
        }
        var value = object.get("with_metadata");
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
          throw new IllegalArgumentException("Expected a boolean for with_metadata");
        }
        withMetadata = value.getAsBoolean();
      }
      switch (operation) {
        case COPY -> {
        }
        case WRITE -> inline = InlineWrite.read(object);
        case PATCH_JSON -> {
          if (!from.getPath().endsWith(".json") || !to.getPath().endsWith(".json")) {
            throw new IllegalArgumentException("patch_json requires JSON file paths");
          }
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
        }
        case RECOLOR_TEXTURE, OVERLAY_TEXTURE -> {
          if (type != PackType.CLIENT_RESOURCES) {
            throw new IllegalArgumentException("Texture rules require client assets");
          }
          secondary = resource(object, operation == Operation.RECOLOR_TEXTURE ? "palette_from" : "overlay");
          mask = operation == Operation.RECOLOR_TEXTURE && object.has("mask") ? resource(object, "mask") : null;
          for (Identifier id : new Identifier[]{from, to, secondary, mask}) {
            if (id != null && (!id.getPath().startsWith("textures/") || !id.getPath().endsWith(".png") || id.getPath().length() <= 13)) {
              throw new IllegalArgumentException("Expected a PNG under textures/: " + id);
            }
          }
        }
      }
      var digest = CacheObservations.digest();
      CacheObservations.text(digest, object.toString());
      Payload payload = switch (operation) {
        case COPY -> new Copy(from, withMetadata);
        case PATCH_JSON -> new Patch(from, List.copyOf(edits));
        case RECOLOR_TEXTURE -> new Recolor(from, secondary, mask);
        case OVERLAY_TEXTURE -> new Overlay(from, secondary);
        case WRITE -> inline;
      };
      return new Rule(payload, to, stage, phase, skip, index, CacheObservations.hex(digest));
    }

    @Nullable
    Identifier from() {
      var inputs = payload.inputs();
      return inputs.isEmpty() ? null : inputs.getFirst();
    }

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
        case Copy copy -> output.copy(to, inputs.get(copy.from()), copy.withMetadata(), MAX_COPY);
        case Patch patch -> {
          try (var input = inputs.get(patch.from()).open(MAX_JSON)) {
            byte[] result = PackIO.json(JsonEdits.apply(readJson(input, MAX_JSON), patch.edits()));
            if (result.length > MAX_JSON) {
              throw new IOException("Patched JSON exceeds " + MAX_JSON + " bytes");
            }
            output.write(to, stream -> stream.write(result));
          }
        }
        case Recolor recolor -> TextureRules.recolor(output, to, inputs.get(recolor.from()), inputs.get(recolor.palette()), inputs.get(recolor.mask()));
        case Overlay overlay -> TextureRules.overlay(output, to, inputs.get(overlay.from()), inputs.get(overlay.overlay()));
        case InlineWrite inline -> inline.generate(context, output, to);
      }
    }
  }

  private sealed interface Payload {
    List<Identifier> inputs();
  }

  private record Copy(Identifier from, boolean withMetadata) implements Payload {
    @Override
    public List<Identifier> inputs() { return List.of(from); }
  }

  private record Patch(Identifier from, List<JsonEdits.Edit> edits) implements Payload {
    @Override
    public List<Identifier> inputs() { return List.of(from); }
  }

  private record Recolor(Identifier from, Identifier palette, @Nullable Identifier mask) implements Payload {
    @Override
    public List<Identifier> inputs() { return mask == null ? List.of(from, palette) : List.of(from, palette, mask); }
  }

  private record Overlay(Identifier from, Identifier overlay) implements Payload {
    @Override
    public List<Identifier> inputs() { return List.of(from, overlay); }
  }

  private enum Destination {
    PRESENT,
    ABSENT
  }

  private record InlineWrite(String contents, @Nullable Destination destination, boolean failOnMismatch) implements Payload {
    @Override
    public List<Identifier> inputs() { return List.of(); }

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
      if (destination == null && object.has("on_mismatch")) {
        throw new IllegalArgumentException("on_mismatch requires if_destination");
      }
      boolean failOnMismatch = switch (string(object, "on_mismatch", "skip")) {
        case "skip" -> false;
        case "fail" -> true;
        default -> throw new IllegalArgumentException("Unknown on_mismatch policy");
      };
      return new InlineWrite(json == null ? string(text) : json.toString(), destination, failOnMismatch);
    }

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
