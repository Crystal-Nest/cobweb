package it.crystalnest.cobweb.api.pack.presentation;

import net.minecraft.client.Minecraft;

/**
 * Provides explicit refresh of title, description, and icon overrides in the active Minecraft pack editor.
 * Use this when a presentation callback depends on state that changes while the editor is open.
 */
public final class PackPresentations {
  private PackPresentations() {}

  /**
   * Clears cached presentation and rebuilds the visible lists for the active resource-pack or data-pack editor.
   * Call on the Minecraft client thread. The refresh affects the editor display without applying the proposed pack selection or reloading resources.
   *
   * @return whether an active pack editor was refreshed.
   */
  public static boolean refresh() {
    if (Minecraft.getInstance().gui.screen() instanceof EditorPackPresentations.Screen screen) {
      screen.refreshPackPresentation();
      return true;
    }
    return false;
  }
}
