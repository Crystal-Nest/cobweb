package it.crystalnest.cobweb.mixin;

import it.crystalnest.cobweb.api.event.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects into {@link ServerPlayer} to add event callback hooks.
 */
@Mixin(ServerPlayer.class)
public class ServerPlayerMixin {
  /**
   * Injects into {@link LivingEntity#die(DamageSource)} at the end.
   * <p>
   * Adds the {@link ServerLivingEntityEvents#AFTER_DEATH} callback hook.
   *
   * @param source damage source.
   * @param ci mixin ci.
   */
  @Inject(method = "die", at = @At("TAIL"))
  private void notifyDeath(DamageSource source, CallbackInfo ci) {
    ServerLivingEntityEvents.AFTER_DEATH.invoker().afterDeath((ServerPlayer) (Object) this, source);
  }
}
