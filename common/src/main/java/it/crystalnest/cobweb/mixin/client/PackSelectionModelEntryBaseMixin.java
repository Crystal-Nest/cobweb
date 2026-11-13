package it.crystalnest.cobweb.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.crystalnest.cobweb.api.pack.client.EditorPackPresentations;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;

/**
 * Injects into the entries of {@link PackSelectionModel} to display editor-specific pack metadata.
 */
@Mixin(targets = "net.minecraft.client.gui.screens.packs.PackSelectionModel$EntryBase")
public abstract class PackSelectionModelEntryBaseMixin {
  @Shadow
  @Final
  private Pack pack;

  @Shadow
  @Final
  private PackSelectionModel this$0;

  @Unique
  private EditorPackPresentations.Model presentationModel() {
    return (EditorPackPresentations.Model) this$0;
  }

  /**
   * Wraps the editor list change callback inside the selection and move methods.<br>
   * Invalidates the old presentation before the selection screen rebuilds its lists.
   *
   * @param instance native list change callback.
   * @param entry entry edited by the user.
   * @param original native callback operation.
   */
  @WrapOperation(method = {"toggleSelection", "move"}, at = @At(value = "INVOKE", target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V"))
  private void wrapAccept(Consumer<Object> instance, Object entry, Operation<Void> original) {
    presentationModel().cobweb$refreshPresentation();
    original.call(instance, entry);
  }

  /**
   * Injects at the start of the method {@link PackSelectionModel.Entry#getTitle()}.<br>
   * Uses the current presentation title when supplied.
   *
   * @param cir callback return information.
   */
  @Inject(method = "getTitle", at = @At("HEAD"), cancellable = true)
  private void onGetTitle(CallbackInfoReturnable<Component> cir) {
    var display = presentationModel().cobweb$presentation(pack);
    if (display != null && display.patch() != null && display.patch().title() != null) {
      cir.setReturnValue(display.patch().title());
    }
  }

  /**
   * Injects at the start of the method {@link PackSelectionModel.Entry#getDescription()}.<br>
   * Uses the current presentation description when supplied.
   *
   * @param cir callback return information.
   */
  @Inject(method = "getDescription", at = @At("HEAD"), cancellable = true)
  private void onGetDescription(CallbackInfoReturnable<Component> cir) {
    var display = presentationModel().cobweb$presentation(pack);
    if (display != null && display.patch() != null && display.patch().description() != null) {
      cir.setReturnValue(display.patch().description());
    }
  }

  /**
   * Injects at the start of the method {@link PackSelectionModel.Entry#getIconTexture()}.<br>
   * Uses the current presentation texture when available.
   *
   * @param cir callback return information.
   */
  @Inject(method = "getIconTexture", at = @At("HEAD"), cancellable = true)
  private void onGetIconTexture(CallbackInfoReturnable<Identifier> cir) {
    var display = presentationModel().cobweb$presentation(pack);
    if (display != null && display.texture() != null) {
      cir.setReturnValue(display.texture());
    }
  }
}
