package it.crystalnest.cobweb.api.pack;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Requires an installed mod, optionally constrained by the loader's version comparison.
 *
 * @param id mod ID.
 * @param version optional version constraint.
 */
public record ModRequirement(String id, @Nullable VersionRange version) {
  /**
   * Creates a mod requirement with a nonblank loader ID.
   *
   * @param id installed mod ID.
   * @param version optional loader version constraint.
   * @throws IllegalArgumentException if the ID is blank.
   */
  public ModRequirement {
    Objects.requireNonNull(id);
    if (id.isBlank()) {
      throw new IllegalArgumentException("Mod ID cannot be blank");
    }
  }

  /**
   * Requires an installed mod independently of version.
   *
   * @param id mod ID.
   */
  public ModRequirement(String id) {
    this(id, null);
  }
}
