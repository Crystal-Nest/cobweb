package it.crystalnest.cobweb.api.pack.generation;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;

import java.io.IOException;

/**
 * Validates and constructs paths used by pack generation and export.
 */
final class PackPaths {
  private PackPaths() {}

  /**
   * Converts an identifier to its pack-relative path and checks path components.
   *
   * @param type native pack type.
   * @param id identifier of the file within its namespace.
   * @return pack-relative path including the native type directory.
   * @throws IOException if the path is invalid.
   */
  static String resourcePath(PackType type, Identifier id) throws IOException {
    if (id.getNamespace().equals(".") || id.getNamespace().equals("..")) {
      throw new IOException("Invalid pack path " + id);
    }
    for (String part : id.getPath().split("/", -1)) {
      if (part.isEmpty() || part.equals(".") || part.equals("..")) {
        throw new IOException("Invalid pack path " + id);
      }
    }
    return type.getDirectory() + "/" + id.getNamespace() + "/" + id.getPath();
  }

  /**
   * Resolves a logical resource identifier to its JSON file.
   *
   * @param directory directory within the resource namespace.
   * @param id logical resource identifier.
   * @return namespaced path to the JSON file.
   */
  static Identifier jsonFile(String directory, Identifier id) {
    return FileToIdConverter.json(directory).idToFile(id);
  }

  /**
   * Resolves a texture identifier to its PNG file.
   *
   * @param id texture identifier without its directory and extension.
   * @return namespaced PNG file under {@code textures}.
   */
  static Identifier texture(Identifier id) {
    return id.withPath(path -> "textures/" + path + ".png");
  }

  /**
   * Resolves a sound identifier to its audio file.
   *
   * @param id sound identifier without its directory and extension.
   * @return namespaced Ogg file under {@code sounds}.
   */
  static Identifier sound(Identifier id) {
    return id.withPath(path -> "sounds/" + path + ".ogg");
  }
}
