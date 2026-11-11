package it.crystalnest.cobweb.api.pack;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Explicit Java metadata overrides. Null fields inherit the pack's file metadata.
 *
 * @param version pack dependency version.
 * @param packs required selected packs.
 * @param mods required installed mods.
 */
@ApiStatus.Internal
public record PackDependencySettings(@Nullable String version, @Nullable List<PackRequirement> packs, @Nullable List<ModRequirement> mods) {
  /**
   * Copies the lists supplied by the Java registrar.
   *
   * @param version optional Java dependency version.
   * @param packs replacement pack requirements, or null to inherit the file list.
   * @param mods replacement mod requirements, or null to inherit the file list.
   */
  public PackDependencySettings {
    packs = packs == null ? null : List.copyOf(packs);
    mods = mods == null ? null : List.copyOf(mods);
  }
}
