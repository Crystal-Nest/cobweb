package it.crystalnest.cobweb.mixin.client;

import it.crystalnest.cobweb.api.pack.client.EditorPackPresentations;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Injects into {@link PackSelectionModel} to scope presentation to the editor's proposed selection.
 */
@Mixin(PackSelectionModel.class)
public abstract class PackSelectionModelMixin implements EditorPackPresentations.Model {
  @Shadow
  @Final
  private PackRepository repository;

  @Shadow
  @Final
  private List<Pack> selected;

  @Unique
  private final EditorPackPresentations presentations = new EditorPackPresentations();

  /**
   * Injects at the return of the method {@link PackSelectionModel#findNewPacks()}.<br>
   * Discards presentation for the previous discovery.
   *
   * @param ci callback information.
   */
  @Inject(method = "findNewPacks", at = @At("RETURN"))
  private void onFindNewPacks(CallbackInfo ci) {
    cobweb$refreshPresentation();
  }

  @Override
  public void cobweb$refreshPresentation() {
    presentations.clear();
  }

  @Override
  @Nullable
  public EditorPackPresentations.Display cobweb$presentation(Pack pack) {
    return presentations.display(pack, repository, selected);
  }
}
