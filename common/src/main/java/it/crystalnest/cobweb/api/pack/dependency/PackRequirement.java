package it.crystalnest.cobweb.api.pack.dependency;

import it.crystalnest.cobweb.api.pack.query.PackReference;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Requires a pack of the same resource type to be selected for the current load.
 * A requirement between packs that generate resources also orders generation after the required pack.
 *
 * @param pack required pack reference.
 * @param version pack version constraint, or {@code null} to require selection only.
 */
public record PackRequirement(PackReference pack, @Nullable VersionRange version) {
  /**
   * @param pack pack reference.
   * @param version pack version constraint, or {@code null} to require selection only.
   */
  public PackRequirement {
    Objects.requireNonNull(pack);
  }

  /**
   * @param pack required pack reference.
   */
  public PackRequirement(PackReference pack) {
    this(pack, null);
  }
}
