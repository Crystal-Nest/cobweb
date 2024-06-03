package it.crystalnest.cobweb.mixin;

import it.crystalnest.cobweb.api.event.ServerLivingEntityEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Injects into {@link LivingEntity} to add event callback hooks.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
  /**
   * Injects into {@link LivingEntity#die(DamageSource)} at the invocation of {@link Level#broadcastEntityEvent(Entity, byte)}.
   * <p>
   * Adds the {@link ServerLivingEntityEvents#AFTER_DEATH} callback hook.
   *
   * @param source damage source.
   * @param ci mixin ci.
   */
  @Inject(method = "die", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;broadcastEntityEvent(Lnet/minecraft/world/entity/Entity;B)V"))
  private void notifyDeath(DamageSource source, CallbackInfo ci) {
    ServerLivingEntityEvents.AFTER_DEATH.invoker().afterDeath((LivingEntity) (Object) this, source);
  }

  /**
   * Injects into {@link LivingEntity#hurt(DamageSource, float)} at the invocation of {@link LivingEntity#isSleeping()}.
   * <p>
   * Adds the {@link ServerLivingEntityEvents#ALLOW_DAMAGE} callback hook.
   *
   * @param source damage source.
   * @param amount damage amount.
   * @param cir mixin cir.
   */
  @Inject(method = "hurt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isSleeping()Z"), cancellable = true)
  private void beforeDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
    if (!ServerLivingEntityEvents.ALLOW_DAMAGE.invoker().allowDamage((LivingEntity) (Object) this, source, amount)) {
      cir.setReturnValue(false);
    }
  }
}
