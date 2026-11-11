package it.crystalnest.cobweb.api.pack;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Identifies a pack by its logical metadata ID or its exact native repository ID.
 *
 * @param logicalId stable logical identifier, if used.
 * @param nativeId exact native ID, if used.
 */
public record PackReference(@Nullable Identifier logicalId, @Nullable String nativeId) {
  /**
   * Creates a reference with exactly one identity.
   *
   * @param logicalId logical identifier, if used.
   * @param nativeId exact native repository ID, if used.
   * @throws IllegalArgumentException if the identities are missing, ambiguous or blank.
   */
  public PackReference {
    if ((logicalId == null) == (nativeId == null) || nativeId != null && nativeId.isBlank()) {
      throw new IllegalArgumentException("Supply exactly one pack identifier");
    }
  }

  /**
   * References a pack by its stable logical ID.
   *
   * @param id logical ID.
   * @return pack reference.
   */
  public static PackReference logical(Identifier id) {
    return new PackReference(Objects.requireNonNull(id), null);
  }

  /**
   * References a pack by its exact repository ID.
   *
   * @param id native ID, including its source prefix if present.
   * @return pack reference.
   */
  public static PackReference nativeId(String id) {
    return new PackReference(null, Objects.requireNonNull(id));
  }
}
