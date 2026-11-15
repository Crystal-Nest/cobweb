package it.crystalnest.cobweb.api.pack.generation.lifecycle;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import org.jetbrains.annotations.Nullable;

/**
 * Summarizes the native result for a participating registered pack.
 *
 * @param pack logical pack ID.
 * @param type native resource type.
 * @param kind initial load or reload.
 * @param failure cause if native loading failed or was canceled.
 */
public record PackLoadOutcome(Identifier pack, PackType type, LoadKind kind, @Nullable Throwable failure) {
  /**
   * Indicates whether native loading completed successfully.
   *
   * @return whether the native load succeeded.
   */
  public boolean successful() {
    return failure == null;
  }
}
