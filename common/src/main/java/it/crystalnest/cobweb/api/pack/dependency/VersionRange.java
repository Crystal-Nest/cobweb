package it.crystalnest.cobweb.api.pack.dependency;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Describes one exact dependency version or one continuous interval with at least one bound.
 * The active loader compares versions for both mod and pack requirements.
 * Each exact version or boundary supplies a single nonblank version string.
 *
 * @param exact exact requested version, or {@code null} for an interval.
 * @param minimum lower bound, or {@code null} when unbounded.
 * @param maximum upper bound, or {@code null} when unbounded.
 */
public record VersionRange(@Nullable String exact, @Nullable Bound minimum, @Nullable Bound maximum) {
  /**
   * @param exact exact requested version, or {@code null} for an interval.
   * @param minimum lower bound, or {@code null} when unbounded.
   * @param maximum upper bound, or {@code null} when unbounded.
   */
  public VersionRange {
    if (exact == null && minimum == null && maximum == null || exact != null && (exact.isBlank() || minimum != null || maximum != null)) {
      throw new IllegalArgumentException("Supply one exact version or at least one interval bound");
    }
  }

  /**
   * Requires one exact version.
   *
   * @param value required version.
   * @return exact version constraint.
   * @throws IllegalArgumentException if the version is blank.
   */
  public static VersionRange exact(String value) {
    return new VersionRange(Objects.requireNonNull(value), null, null);
  }

  /**
   * Requires a version within the given bounds according to the active loader.
   *
   * @param minimum lower bound, or {@code null} when unbounded.
   * @param maximum upper bound, or {@code null} when unbounded.
   * @return interval constraint.
   * @throws IllegalArgumentException if both bounds are absent.
   */
  public static VersionRange interval(@Nullable Bound minimum, @Nullable Bound maximum) {
    return new VersionRange(null, minimum, maximum);
  }

  /**
   * Represents a lower or upper boundary with a nonblank version.
   *
   * @param version version at the boundary.
   * @param inclusive whether the boundary is included.
   */
  public record Bound(String version, boolean inclusive) {
    /**
     * @param version version at the boundary.
     * @param inclusive whether the boundary is included.
     */
    public Bound {
      Objects.requireNonNull(version);
      if (version.isBlank()) {
        throw new IllegalArgumentException("Version bound cannot be blank");
      }
    }
  }
}
