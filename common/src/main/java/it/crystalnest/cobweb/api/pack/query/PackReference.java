package it.crystalnest.cobweb.api.pack.query;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * Identifies which ID to match when looking up a pack.
 * A reference uses either the logical metadata ID or the exact native repository ID.
 */
public sealed interface PackReference permits PackReference.Logical, PackReference.Native {
  /**
   * References a pack by its stable logical ID.
   *
   * @param id logical ID.
   * @return logical pack reference.
   * @throws NullPointerException if the ID is {@code null}.
   */
  static Logical logical(Identifier id) {
    return new Logical(id);
  }

  /**
   * References a pack by its exact repository ID.
   *
   * @param id native ID, including its source prefix if present.
   * @return native pack reference.
   * @throws NullPointerException if the ID is {@code null}.
   * @throws IllegalArgumentException if the ID is blank.
   */
  static Native nativeId(String id) {
    return new Native(id);
  }

  /**
   * References a pack by its stable namespaced ID.
   *
   * @param id logical ID.
   */
  record Logical(Identifier id) implements PackReference {
    /**
     * @param id non-null logical ID.
     */
    public Logical {
      Objects.requireNonNull(id, "Logical pack ID");
    }
  }

  /**
   * References a pack by the exact string used in Minecraft's repository.
   *
   * @param id native ID, including its source prefix if present.
   */
  record Native(String id) implements PackReference {
    /**
     * @param id non-blank native ID.
     */
    public Native {
      if (Objects.requireNonNull(id, "Native pack ID").isBlank()) {
        throw new IllegalArgumentException("Native pack ID must not be blank");
      }
    }
  }
}
