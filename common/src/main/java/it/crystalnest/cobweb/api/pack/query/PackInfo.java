package it.crystalnest.cobweb.api.pack.query;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Identifies a pack in one native observation.
 *
 * @param nativeId native pack repository ID.
 * @param logicalId optional logical dependency ID.
 */
public record PackInfo(String nativeId, @Nullable Identifier logicalId) {
  /**
   * Checks whether this observation matches the supplied pack reference.
   *
   * @param reference queried identity.
   * @return whether the reference identifies this pack.
   */
  public boolean matches(PackReference reference) {
    return reference.nativeId() == null ? reference.logicalId().equals(logicalId) : reference.nativeId().equals(nativeId);
  }
}
