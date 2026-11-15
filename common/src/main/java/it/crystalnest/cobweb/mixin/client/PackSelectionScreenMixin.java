package it.crystalnest.cobweb.mixin.client;

import it.crystalnest.cobweb.api.pack.presentation.EditorPackPresentations;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Injects into {@link PackSelectionScreen} to release editor textures and support explicit presentation refreshes.
 */
@Mixin(PackSelectionScreen.class)
public abstract class PackSelectionScreenMixin extends Screen implements EditorPackPresentations.Screen {
  /**
   * Selection model displayed by this screen.
   */
  @Shadow
  @Final
  private PackSelectionModel model;

  /**
   * Rebuilds the native available and selected pack lists.
   *
   * @param transferredEntry entry whose selection changed, or {@code null} for a general refresh.
   */
  @Shadow
  private void populateLists(PackSelectionModel.EntryBase transferredEntry) {
    throw new AssertionError();
  }

  /**
   * @param title screen title.
   */
  protected PackSelectionScreenMixin(Component title) {
    super(title);
  }

  @Override
  public void refreshPackPresentation() {
    ((EditorPackPresentations.Model) model).invalidatePackPresentation();
    populateLists(null);
  }

  @Override
  public void removed() {
    ((EditorPackPresentations.Model) model).invalidatePackPresentation();
    super.removed();
  }
}
