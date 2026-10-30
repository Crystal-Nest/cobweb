package it.crystalnest.cobweb.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.crystalnest.cobweb.impl.pack.client.ClientPackSelection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects into {@link Options} to persist static asset pack selection while preserving native filtering.
 */
@Mixin(Options.class)
public abstract class OptionsMixin {
  /**
   * Client instance owning these options and the current game directory.
   */
  @Shadow
  @Final
  private Minecraft minecraft;

  /**
   * Injects at the return of the method {@link Options#loadSelectedResourcePacks(PackRepository)}.<br>
   * Applies first-discovery defaults after native saved-selection filtering.
   *
   * @param repository discovered native resource-pack repository.
   * @param ci native callback.
   */
  @Inject(method = "loadSelectedResourcePacks", at = @At("RETURN"))
  private void onLoadSelectedResourcePacks(PackRepository repository, CallbackInfo ci) {
    ClientPackSelection.initialize((Options) (Object) this, repository, minecraft.gameDirectory.toPath());
  }

  /**
   * Wraps the call to {@link Pack#isFixedPosition()} inside the method {@link Options#updateResourcePacks(PackRepository)}.<br>
   * Allows visible Cobweb packs to be saved even when their position is fixed.
   *
   * @param instance pack being considered for saving.
   * @param original native fixed-position operation, including other loader hooks.
   * @return whether native saving should treat the pack as fixed and exclude it.
   */
  @WrapOperation(method = "updateResourcePacks", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/packs/repository/Pack;isFixedPosition()Z"))
  private boolean wrapIsFixedPosition(Pack instance, Operation<Boolean> original) {
    return original.call(instance) && !ClientPackSelection.persistFixed(instance);
  }
}
