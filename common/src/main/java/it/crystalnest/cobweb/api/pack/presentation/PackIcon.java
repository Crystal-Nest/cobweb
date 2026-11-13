package it.crystalnest.cobweb.api.pack.presentation;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * Locates an icon relative to a mod, a bundled pack, or the applied client resource manager.
 *
 * @param origin icon origin.
 * @param resource namespaced resource or relative pack-root path.
 */
public record PackIcon(Origin origin, Identifier resource) {
  /**
   * Creates an icon source with a path confined to that source.
   *
   * @param origin resource origin.
   * @param resource namespaced file path.
   * @throws IllegalArgumentException if the path contains an empty or relative component.
   */
  public PackIcon {
    Objects.requireNonNull(origin);
    Objects.requireNonNull(resource);
    for (String part : resource.getPath().split("/", -1)) {
      if (part.isEmpty() || part.equals(".") || part.equals("..")) {
        throw new IllegalArgumentException("Invalid pack icon path: " + resource);
      }
    }
  }

  /**
   * Resolves an icon from the named mod's ordinary bundled assets.
   *
   * @param id mod namespace and path relative to its assets namespace.
   * @return icon source.
   */
  public static PackIcon modResource(Identifier id) {
    return new PackIcon(Origin.MOD, id);
  }

  /**
   * Resolves an icon relative to the displayed pack's own root.
   *
   * @param path path relative to the root, such as {@code icons/active.png}.
   * @return icon source.
   */
  public static PackIcon bundledPack(String path) {
    return new PackIcon(Origin.PACK, Identifier.withDefaultNamespace(path));
  }

  /**
   * Resolves an icon through the applied client assets and native precedence.
   *
   * @param id client resource identifier, including its extension.
   * @return icon source.
   */
  public static PackIcon appliedResource(Identifier id) {
    return new PackIcon(Origin.APPLIED, id);
  }

  /**
   * Declares the root used to resolve icon bytes.
   */
  public enum Origin {
    /**
     * Resources physically stored in the owning mod.
     */
    MOD,

    /**
     * Resources physically stored at the displayed pack's root.
     */
    PACK,

    /**
     * Resources supplied by the applied client asset manager.
     */
    APPLIED
  }
}
