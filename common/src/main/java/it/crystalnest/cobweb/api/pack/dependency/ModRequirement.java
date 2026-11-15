package it.crystalnest.cobweb.api.pack.dependency;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Requires a loaded mod and optionally checks its version using the active loader's rules.
 * The mod ID must be nonblank.
 *
 * @param id mod ID.
 * @param version loader version constraint, or {@code null} to require presence only.
 */
public record ModRequirement(String id, @Nullable VersionRange version) {
  /**
   * @param id installed mod ID.
   * @param version loader version constraint, or {@code null} to require presence only.
   */
  public ModRequirement {
    Objects.requireNonNull(id);
    if (id.isBlank()) {
      throw new IllegalArgumentException("Mod ID cannot be blank");
    }
  }

  /**
   * @param id mod ID.
   */
  public ModRequirement(String id) {
    this(id, null);
  }
}
