package it.crystalnest.cobweb.impl.pack;

import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import org.jetbrains.annotations.ApiStatus;

/**
 * A native bundled pack opening and the loader's root traversal for exports.
 *
 * @param pack native resource opening.
 * @param listing traversal of all resources within one namespace.
 */
@ApiStatus.Internal
public record BundledPackResources(PackResources pack, Listing listing) {
  /**
   * Enumerates all files in one namespace without assuming a first directory segment.
   */
  @FunctionalInterface
  public interface Listing {
    /**
     * Lists resources using the loader's bundled resource container.
     *
     * @param type resource side.
     * @param namespace resource namespace.
     * @param output file receiver.
     */
    void list(PackType type, String namespace, PackResources.ResourceOutput output);
  }
}
