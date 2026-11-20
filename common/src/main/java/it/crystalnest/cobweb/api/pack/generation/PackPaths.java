package it.crystalnest.cobweb.api.pack.generation;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;

import java.io.IOException;

/**
 * Validates and constructs paths used by pack generation and export.
 */
final class PackPaths {
  /**
   * Directory containing native structure resources.
   */
  static final String STRUCTURE_DIRECTORY = "structure";

  /**
   * Native structure file extension.
   */
  static final String STRUCTURE_EXTENSION = ".nbt";

  /**
   * Texture path prefix within a namespace.
   */
  private static final String TEXTURE_PREFIX = "textures/";

  /**
   * Encoded texture file extension.
   */
  private static final String TEXTURE_EXTENSION = ".png";

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
    if (".".equals(id.getNamespace()) || "..".equals(id.getNamespace())) {
      throw new IOException("Invalid pack path " + id);
    }
    for (String part : id.getPath().split("/", -1)) {
      if (part.isEmpty() || ".".equals(part) || "..".equals(part)) {
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
    return id.withPath(path -> TEXTURE_PREFIX + path + TEXTURE_EXTENSION);
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

  /**
   * Resolves a complete translation file without validating an intermediate identifier.
   *
   * @param namespace resource namespace.
   * @param language requested language code.
   * @return namespaced language file identifier.
   */
  static Identifier language(String namespace, String language) {
    return Identifier.fromNamespaceAndPath(namespace, "lang/" + language + ".json");
  }

  /**
   * Resolves a logical structure identifier to its native NBT file.
   *
   * @param id structure identifier without directory and extension.
   * @return namespaced structure file identifier.
   */
  static Identifier structure(Identifier id) {
    return id.withPath(path -> STRUCTURE_DIRECTORY + "/" + path + STRUCTURE_EXTENSION);
  }

  /**
   * Checks the directory, extension and nonempty base name of a full texture path.
   *
   * @param id full namespaced resource path.
   * @return whether the path names a PNG beneath the texture directory.
   */
  static boolean isTexture(Identifier id) {
    String path = id.getPath();
    return path.startsWith(TEXTURE_PREFIX) && path.endsWith(TEXTURE_EXTENSION) && path.length() > TEXTURE_PREFIX.length() + TEXTURE_EXTENSION.length();
  }

  /**
   * Removes the directory and extension from an already validated full texture path.
   *
   * @param file full namespaced PNG path beneath the texture directory.
   * @return logical texture identifier.
   */
  static Identifier textureId(Identifier file) {
    return file.withPath(path -> path.substring(TEXTURE_PREFIX.length(), path.length() - TEXTURE_EXTENSION.length()));
  }
}
