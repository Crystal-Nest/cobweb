package it.crystalnest.cobweb.api.pack.dependency;

import it.crystalnest.cobweb.api.pack.query.PackReference;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Requires a pack of the same resource type to participate in the current load.
 *
 * @param pack required pack reference.
 * @param version optional version constraint.
 */
public record PackRequirement(PackReference pack, @Nullable VersionRange version) {
  /**
   * Creates a requirement on the selected pack and its optional version.
   *
   * @param pack pack reference.
   * @param version optional version constraint.
   */
  public PackRequirement {
    Objects.requireNonNull(pack);
  }

  /**
   * Requires selected participation independently of version.
   *
   * @param pack required pack reference.
   */
  public PackRequirement(PackReference pack) {
    this(pack, null);
  }
}
