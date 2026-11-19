package it.crystalnest.cobweb.api.pack.generation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Applies the supported JSON Patch edits to a complete document.
 */
final class JsonEdits {
  /**
   * Canonical nonnegative array indices accepted by JSON Pointer edits.
   */
  private static final Pattern ARRAY_INDEX = Pattern.compile("0|[1-9]\\d*");

  private JsonEdits() {}

  /**
   * Applies the edits in declaration order to a copy of the input.
   *
   * @param document original document.
   * @param edits ordered edits.
   * @return edited JSON document.
   */
  static JsonElement apply(JsonElement document, List<Edit> edits) {
    for (Edit edit : edits) {
      document = edit.apply(document);
    }
    return document;
  }

  /**
   * Decodes a JSON Pointer into its path components.
   *
   * @param path JSON Pointer string.
   * @return decoded pointer components.
   */
  static List<String> pointer(String path) {
    if (!path.isEmpty()) {
      if (path.startsWith("/")) {
        return Arrays.stream(path.substring(1).split("/", -1)).map(JsonEdits::unescape).toList();
      }
      throw new IllegalArgumentException("Invalid JSON Pointer " + path);
    }
    return List.of();
  }

  /**
   * Decodes JSON Pointer escapes for one component.
   *
   * @param part encoded pointer component.
   * @return unescaped component.
   */
  private static String unescape(String part) {
    var key = new StringBuilder();
    int index = 0;
    while (index < part.length()) {
      char character = part.charAt(index++);
      if (character != '~') {
        key.append(character);
      } else {
        if (index == part.length()) {
          throw new IllegalArgumentException("Incomplete JSON Pointer escape");
        }
        key.append(switch (part.charAt(index++)) {
          case '0' -> '~';
          case '1' -> '/';
          default -> throw new IllegalArgumentException("Invalid JSON Pointer escape in " + part);
        });
      }
    }
    return key.toString();
  }

  /**
   * Identifies a supported JSON edit.
   */
  enum Operation {
    /**
     * Adds a property or inserts an array item.
     */
    ADD,
    /**
     * Replaces an existing property, array item or document.
     */
    REPLACE,
    /**
     * Removes an existing property or array item.
     */
    REMOVE
  }

  /**
   * Describes one operation at a decoded JSON Pointer.
   *
   * @param operation edit operation.
   * @param path decoded pointer components.
   * @param value replacement or added value, unused for removal.
   */
  record Edit(Operation operation, List<String> path, JsonElement value) {
    /**
     * Applies this edit to its document.
     *
     * @param document mutable working document.
     * @return updated document, which may have a new root.
     */
    JsonElement apply(JsonElement document) {
      if (!path.isEmpty()) {
        JsonElement parent = document;
        for (int i = 0; i < path.size() - 1; i++) {
          parent = child(parent, path.get(i));
        }
        String key = path.getLast();
        if (parent.isJsonArray()) {
          var array = parent.getAsJsonArray();
          int index = index(array, key, operation == Operation.ADD);
          switch (operation) {
            case ADD -> array.asList().add(index, value.deepCopy());
            case REPLACE -> array.set(index, value.deepCopy());
            case REMOVE -> array.remove(index);
          }
        } else if (parent.isJsonObject()) {
          var object = parent.getAsJsonObject();
          if (operation != Operation.ADD && !object.has(key)) {
            throw new IllegalArgumentException("Missing JSON member " + key);
          }
          if (operation == Operation.REMOVE) {
            object.remove(key);
          } else {
            object.add(key, value.deepCopy());
          }
        } else {
          throw new IllegalArgumentException("JSON Pointer parent must be an object or array");
        }
        return document;
      }
      if (operation == Operation.REMOVE) {
        throw new IllegalArgumentException("A JSON rule must produce a complete document");
      }
      return value.deepCopy();
    }

    /**
     * Validates a JSON array position for the current edit.
     *
     * @param array parent JSON array.
     * @param key pointer component naming the index.
     * @param add whether the edit is an addition.
     * @return array index to access or insert.
     */
    private static int index(JsonArray array, String key, boolean add) {
      if (add && "-".equals(key)) {
        return array.size();
      }
      if (ARRAY_INDEX.matcher(key).matches()) {
        int value = Integer.parseInt(key);
        if (!(value > array.size() || !add && value == array.size())) {
          return value;
        }
        throw new IllegalArgumentException("JSON array index is out of range: " + key);
      }
      throw new IllegalArgumentException("Invalid JSON array index " + key);
    }

    /**
     * Resolves an existing child within an object or array.
     *
     * @param parent parent JSON container.
     * @param key pointer component naming the child.
     * @return child selected by the pointer component.
     */
    private static JsonElement child(JsonElement parent, String key) {
      if (parent.isJsonArray()) {
        var array = parent.getAsJsonArray();
        return array.get(index(array, key, false));
      }
      if (parent.isJsonObject() && parent.getAsJsonObject().has(key)) {
        return parent.getAsJsonObject().get(key);
      }
      throw new IllegalArgumentException("Missing JSON member " + key);
    }
  }
}
