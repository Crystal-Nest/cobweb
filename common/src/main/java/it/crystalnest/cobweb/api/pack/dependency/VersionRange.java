package it.crystalnest.cobweb.api.pack.dependency;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Describes one exact version or one continuous interval with at least one bound.
 * The active loader compares version strings for both mod and pack requirements.
 */
public sealed interface VersionRange permits VersionRange.Exact, VersionRange.Interval {
  /**
   * Creates an exact version requirement.
   *
   * @param version required version string.
   * @return exact version requirement.
   * @throws NullPointerException if the version is {@code null}.
   * @throws IllegalArgumentException if the version is blank.
   */
  static Exact exact(String version) {
    return new Exact(version);
  }

  /**
   * Creates an interval with one or two endpoints.
   * The active loader determines endpoint ordering and comparison behavior.
   *
   * @param minimum lower bound, or {@code null} when unbounded below.
   * @param maximum upper bound, or {@code null} when unbounded above.
   * @return interval requirement.
   * @throws IllegalArgumentException if both endpoints are absent.
   */
  static Interval interval(@Nullable Bound minimum, @Nullable Bound maximum) {
    return new Interval(minimum, maximum);
  }

  /**
   * Validates a version string without interpreting the loader's version syntax.
   *
   * @param version version string to validate.
   * @return supplied nonblank version.
   * @throws NullPointerException if the version is {@code null}.
   * @throws IllegalArgumentException if the version is blank.
   */
  private static String requireVersion(String version) {
    if (!Objects.requireNonNull(version).isBlank()) {
      return version;
    }
    throw new IllegalArgumentException("Version must not be blank");
  }

  /**
   * Requires an exact version without exposing irrelevant interval fields.
   *
   * @param version required nonblank version string.
   */
  record Exact(String version) implements VersionRange {
    /**
     * @param version required nonblank version string.
     */
    public Exact {
      version = requireVersion(version);
    }
  }

  /**
   * Requires a continuous interval with at least one endpoint.
   * Missing endpoints represent unbounded sides; two missing endpoints are invalid.
   *
   * @param minimum lower bound, or {@code null} when unbounded below.
   * @param maximum upper bound, or {@code null} when unbounded above.
   */
  record Interval(@Nullable Bound minimum, @Nullable Bound maximum) implements VersionRange {
    /**
     * @param minimum lower bound, or {@code null} when unbounded below.
     * @param maximum upper bound, or {@code null} when unbounded above.
     */
    public Interval {
      if (minimum == null && maximum == null) {
        throw new IllegalArgumentException("Supply at least one interval bound");
      }
    }
  }

  /**
   * Represents one inclusive or exclusive endpoint.
   * Its position in an interval determines whether it is the lower or upper bound.
   *
   * @param version nonblank version string at the endpoint.
   * @param inclusion endpoint inclusion policy.
   */
  record Bound(String version, Bound.Inclusion inclusion) {
    /**
     * @param version nonblank version string at the endpoint.
     * @param inclusion non-null endpoint inclusion policy.
     */
    public Bound {
      version = requireVersion(version);
      Objects.requireNonNull(inclusion);
    }

    /**
     * Creates a bound that includes its endpoint.
     *
     * @param version version at the endpoint.
     * @return inclusive bound.
     * @throws NullPointerException if the version is {@code null}.
     * @throws IllegalArgumentException if the version is blank.
     */
    public static Bound inclusive(String version) {
      return new Bound(version, Inclusion.INCLUSIVE);
    }

    /**
     * Creates a bound that excludes its endpoint.
     *
     * @param version version at the endpoint.
     * @return exclusive bound.
     * @throws NullPointerException if the version is {@code null}.
     * @throws IllegalArgumentException if the version is blank.
     */
    public static Bound exclusive(String version) {
      return new Bound(version, Inclusion.EXCLUSIVE);
    }

    /**
     * Determines whether an interval accepts its endpoint.
     */
    public enum Inclusion {
      /**
       * Accepts the endpoint itself.
       */
      INCLUSIVE,
      /**
       * Excludes the endpoint itself.
       */
      EXCLUSIVE
    }
  }
}
