package it.crystalnest.cobweb.api.pack.client;

import it.crystalnest.cobweb.impl.pack.client.EditorPackPresentations;
import net.minecraft.client.Minecraft;

/**
 * Controls presentation in the active client pack selection screen.
 */
public final class PackPresentations {
  private PackPresentations() {}

  /**
   * Refreshes the displayed pack metadata on the native client thread.
   *
   * @return whether the client has an active pack selection editor.
   */
  public static boolean refresh() {
    if (Minecraft.getInstance().gui.screen() instanceof EditorPackPresentations.Screen screen) {
      screen.cobweb$refreshPackPresentation();
      return true;
    }
    return false;
  }
}
