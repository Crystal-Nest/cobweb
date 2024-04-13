package it.crystalnest.cobweb_mod_template.mixin;

import it.crystalnest.cobweb_mod_template.Constants;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Example mixin class.
 */
@Mixin(TitleScreen.class)
public class TitleScreenMixin {
  /**
   * Injects at the end of the base constructor to print an example message.
   *
   * @param fading
   * @param logoRenderer
   * @param ci
   */
  @Inject(method = "<init>(ZLnet/minecraft/client/gui/components/LogoRenderer;)V", at = @At(value = "TAIL"))
  private void onInit(boolean fading, LogoRenderer logoRenderer, CallbackInfo ci) {
    Constants.LOGGER.info("Example mixin from " + Constants.PLATFORM.getPlatformName());
  }
}
