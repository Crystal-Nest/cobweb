package it.crystalnest.cobweb.api.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.Arrays;
import java.util.List;

/**
 * Applies the supported JSON Patch edits to a complete document.
 */
final class JsonEdits {
  private JsonEdits() {}

  static JsonElement apply(JsonElement document, List<Edit> edits) {
    for (Edit edit : edits) {
      document = edit.apply(document);
    }
    return document;
  }

  static List<String> pointer(String path) {
    if (path.isEmpty()) {
      return List.of();
    }
    if (!path.startsWith("/")) {
      throw new IllegalArgumentException("Invalid JSON Pointer " + path);
    }
    return Arrays.stream(path.substring(1).split("/", -1)).map(JsonEdits::unescape).toList();
  }

  private static String unescape(String part) {
    var key = new StringBuilder();
    for (int i = 0; i < part.length(); i++) {
      char character = part.charAt(i);
      if (character != '~') {
        key.append(character);
      } else {
        if (++i == part.length()) {
          throw new IllegalArgumentException("Incomplete JSON Pointer escape");
        }
        key.append(switch (part.charAt(i)) {
          case '0' -> '~';
          case '1' -> '/';
          default -> throw new IllegalArgumentException("Invalid JSON Pointer escape in " + part);
        });
      }
    }
    return key.toString();
  }

  private static int index(JsonArray array, String key, boolean add) {
    if (add && key.equals("-")) {
      return array.size();
    }
    if (!key.matches("0|[1-9][0-9]*")) {
      throw new IllegalArgumentException("Invalid JSON array index " + key);
    }
    int value = Integer.parseInt(key);
    if (value > array.size() || !add && value == array.size()) {
      throw new IllegalArgumentException("JSON array index is out of range: " + key);
    }
    return value;
  }

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

  enum Operation {
    ADD,
    REPLACE,
    REMOVE
  }

  record Edit(Operation operation, List<String> path, JsonElement value) {
    JsonElement apply(JsonElement document) {
      if (path.isEmpty()) {
        if (operation == Operation.REMOVE) {
          throw new IllegalArgumentException("A JSON rule must produce a complete document");
        }
        return value.deepCopy();
      }
      JsonElement parent = document;
      for (int i = 0; i < path.size() - 1; i++) {
        parent = child(parent, path.get(i));
      }
      String key = path.getLast();
      if (parent.isJsonArray()) {
        var array = parent.getAsJsonArray();
        int index = index(array, key, operation == Operation.ADD);
        switch (operation) {
          case ADD -> {
            array.asList().add(index, value.deepCopy());
          }
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
  }
}
