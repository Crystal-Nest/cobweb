package it.crystalnest.cobweb.mixin.client;

import it.crystalnest.cobweb.impl.pack.client.EditorPackPresentations;
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
  @Shadow
  @Final
  private PackSelectionModel model;

  @Shadow
  private void populateLists(PackSelectionModel.EntryBase transferredEntry) {
    throw new AssertionError();
  }

  /**
   * Matches the title constructor of the native screen base.
   *
   * @param title screen title.
   */
  protected PackSelectionScreenMixin(Component title) {
    super(title);
  }

  @Override
  public void cobweb$refreshPackPresentation() {
    ((EditorPackPresentations.Model) model).cobweb$refreshPresentation();
    populateLists(null);
  }

  @Override
  public void removed() {
    ((EditorPackPresentations.Model) model).cobweb$refreshPresentation();
    super.removed();
  }
}
