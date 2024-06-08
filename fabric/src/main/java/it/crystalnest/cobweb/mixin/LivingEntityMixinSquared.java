package it.crystalnest.cobweb.mixin;

import com.bawnorton.mixinsquared.TargetHandler;
import it.crystalnest.cobweb.api.event.ServerLivingEntityEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Injects into the Fabric API LivingEntityMixin to alter event handling.
 */
@Mixin(value = LivingEntity.class, priority = 2000)
public abstract class LivingEntityMixinSquared {
  /**
   * Shadowed {@link LivingEntity#isDeadOrDying()}.
   */
  @Shadow
  public abstract boolean isDeadOrDying();

  /**
   * Injects into {@link net.fabricmc.fabric.mixin.entity.event.LivingEntityMixin#beforePlayerKilled(LivingEntity, DamageSource, float)} at the start.
   * <p>
   * Overwrites the original handler and triggers the {@link ServerLivingEntityEvents#ALLOW_DEATH} event.
   *
   * @param livingEntity living entity dying.
   * @param source damage source.
   * @param amount damage amount.
   * @param cir mixin cir.
   */
  @TargetHandler(mixin = "net.fabricmc.fabric.mixin.entity.event.LivingEntityMixin", name = "beforePlayerKilled")
  @Inject(method = "@MixinSquared:Handler", at = @At(value = "HEAD"), cancellable = true)
  private void beforeEntityKilled(LivingEntity livingEntity, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
    cir.setReturnValue(isDeadOrDying() && ServerLivingEntityEvents.ALLOW_DEATH.invoker().allowDeath(livingEntity, source, amount));
  }
}
