package it.crystalnest.cobweb.api.pack;

import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import org.jetbrains.annotations.ApiStatus;

/**
 * Pairs one open bundled pack root with a traversal used by pack exports and publication.
 * <p>The native resources provide ordinary lookup and metadata. The traversal also includes files at the namespace root.
 * The owner closes the native resources when the opening is no longer in use.
 *
 * @param pack open native resources for the base pack or one overlay.
 * @param listing traversal of all files in one namespace of this opening.
 */
@ApiStatus.Internal
public record BundledPackResources(PackResources pack, Listing listing) {
  /**
   * Enumerates all files in one namespace of an open pack, including files directly at its namespace root.
   */
  @FunctionalInterface
  public interface Listing {
    /**
     * Passes each resource identifier and its stream supplier to the receiver.
     *
     * @param type native pack type to traverse.
     * @param namespace namespace whose files to list.
     * @param output receiver of namespaced file identifiers and stream suppliers.
     */
    void list(PackType type, String namespace, PackResources.ResourceOutput output);
  }
}
