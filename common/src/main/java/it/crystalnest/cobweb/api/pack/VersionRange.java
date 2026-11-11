package it.crystalnest.cobweb.api.pack;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * One exact dependency version or one continuous interval.
 *
 * @param exact exact requested version.
 * @param minimum optional lower bound.
 * @param maximum optional upper bound.
 * @param allowPrerelease whether a pack interval may match unstable versions.
 */
public record VersionRange(@Nullable String exact, @Nullable Bound minimum, @Nullable Bound maximum, boolean allowPrerelease) {
  /**
   * Creates an exact version requirement or one interval.
   *
   * @param exact optional exact version.
   * @param minimum optional lower bound.
   * @param maximum optional upper bound.
   * @param allowPrerelease whether an interval can match a prerelease.
   * @throws IllegalArgumentException if the version constraint is empty or ambiguous.
   */
  public VersionRange {
    if (exact == null && minimum == null && maximum == null || exact != null && (exact.isBlank() || minimum != null || maximum != null || allowPrerelease)) {
      throw new IllegalArgumentException("Supply one exact version or at least one interval bound");
    }
  }

  /**
   * Requires one exact version.
   *
   * @param value required version.
   * @return exact version constraint.
   */
  public static VersionRange exact(String value) {
    return new VersionRange(Objects.requireNonNull(value), null, null, false);
  }

  /**
   * Requires a version within the given bounds. Pack intervals select stable versions by default.
   *
   * @param minimum optional lower bound.
   * @param maximum optional upper bound.
   * @return interval constraint.
   */
  public static VersionRange interval(@Nullable Bound minimum, @Nullable Bound maximum) {
    return new VersionRange(null, minimum, maximum, false);
  }

  /**
   * Represents a lower or upper version bound.
   *
   * @param version version at the boundary.
   * @param inclusive whether the boundary is included.
   */
  public record Bound(String version, boolean inclusive) {
    /**
     * Creates a version boundary.
     *
     * @param version version at the boundary.
     * @param inclusive whether the boundary is included.
     * @throws IllegalArgumentException if the version is blank.
     */
    public Bound {
      Objects.requireNonNull(version);
      if (version.isBlank()) {
        throw new IllegalArgumentException("Version bound cannot be blank");
      }
    }
  }
}
