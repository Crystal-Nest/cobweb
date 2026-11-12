package it.crystalnest.cobweb.api.pack;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;

import java.io.IOException;

/**
 * Validates resource paths and constructs common media paths independently of export.
 */
final class PackPaths {
  private PackPaths() {}

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

  static Identifier texture(Identifier id) {
    return id.withPath(path -> "textures/" + path + ".png");
  }

  static Identifier sound(Identifier id) {
    return id.withPath(path -> "sounds/" + path + ".ogg");
  }
}
