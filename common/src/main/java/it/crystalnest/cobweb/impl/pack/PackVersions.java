package it.crystalnest.cobweb.impl.pack;

import it.crystalnest.cobweb.api.pack.dependency.VersionRange;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates and compares logical pack versions independently of Minecraft and loader versions.
 */
final class PackVersions {
  private static final Pattern VERSION = Pattern.compile("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?");

  private PackVersions() {}

  static void validate(String version) {
    parse(version);
  }

  static boolean matches(@Nullable String supplied, VersionRange range) {
    Version exact = range.exact() == null ? null : parse(range.exact());
    Version lower = range.minimum() == null ? null : parse(range.minimum().version());
    Version upper = range.maximum() == null ? null : parse(range.maximum().version());
    if (lower != null && upper != null) {
      int order = lower.compareTo(upper);
      if (order > 0 || order == 0 && (!range.minimum().inclusive() || !range.maximum().inclusive())) {
        throw new IllegalArgumentException("Empty pack version interval: " + range);
      }
    }
    if (supplied == null) {
      return false;
    }
    Version actual = parse(supplied);
    if (exact != null) {
      return actual.compareTo(exact) == 0;
    }
    if (!range.allowPrerelease() && (actual.major.signum() == 0 || !actual.prerelease.isEmpty())) {
      return false;
    }
    int min = lower == null ? 1 : actual.compareTo(lower);
    int max = upper == null ? -1 : actual.compareTo(upper);
    return (min > 0 || min == 0 && range.minimum().inclusive()) && (max < 0 || max == 0 && range.maximum().inclusive());
  }

  private static Version parse(String value) {
    var matcher = VERSION.matcher(value);
    if (!matcher.matches()) {
      throw new IllegalArgumentException("Invalid logical pack version: " + value);
    }
    List<String> prerelease = matcher.group(4) == null ? List.of() : List.of(matcher.group(4).split("\\."));
    for (String part : prerelease) {
      if (numeric(part) && part.length() > 1 && part.charAt(0) == '0') {
        throw new IllegalArgumentException("Invalid numeric prerelease component in " + value);
      }
    }
    return new Version(new BigInteger(matcher.group(1)), new BigInteger(matcher.group(2)), new BigInteger(matcher.group(3)), prerelease);
  }

  private static boolean numeric(String value) {
    return value.chars().allMatch(character -> character >= '0' && character <= '9');
  }

  private record Version(BigInteger major, BigInteger minor, BigInteger patch, List<String> prerelease) implements Comparable<Version> {
    @Override
    public int compareTo(Version other) {
      int compare = major.compareTo(other.major);
      if (compare == 0) {
        compare = minor.compareTo(other.minor);
      }
      if (compare == 0) {
        compare = patch.compareTo(other.patch);
      }
      if (compare != 0) {
        return compare;
      }
      if (prerelease.isEmpty() || other.prerelease.isEmpty()) {
        return Boolean.compare(prerelease.isEmpty(), other.prerelease.isEmpty());
      }
      for (int index = 0; index < Math.min(prerelease.size(), other.prerelease.size()); index++) {
        String left = prerelease.get(index);
        String right = other.prerelease.get(index);
        boolean leftNumeric = numeric(left);
        boolean rightNumeric = numeric(right);
        compare = leftNumeric && rightNumeric ? new BigInteger(left).compareTo(new BigInteger(right))
          : leftNumeric != rightNumeric ? Boolean.compare(rightNumeric, leftNumeric)
          : left.compareTo(right);
        if (compare != 0) {
          return compare;
        }
      }
      return Integer.compare(prerelease.size(), other.prerelease.size());
    }
  }
}
