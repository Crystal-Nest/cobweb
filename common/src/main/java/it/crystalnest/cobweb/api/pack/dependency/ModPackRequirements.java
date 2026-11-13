package it.crystalnest.cobweb.api.pack.dependency;

import it.crystalnest.cobweb.impl.pack.NativePackDependencies;
import net.minecraft.server.packs.PackType;

import java.util.List;

/**
 * Declares the pack requirements of an installed mod during initialization.
 */
public final class ModPackRequirements {
  private ModPackRequirements() {}

  /**
   * Replaces one of the mod's file requirement lists. An empty list clears it.
   *
   * @param modId declaring mod's loader ID.
   * @param type resource type of the required packs.
   * @param requirements required selected packs.
   */
  public static void declare(String modId, PackType type, PackRequirement... requirements) {
    NativePackDependencies.declareModRequirements(modId, type, List.of(requirements));
  }
}
