package it.crystalnest.cobweb.mixin.client;

import it.crystalnest.cobweb.platform.FabricRegistryHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects into {@link Minecraft} to add dynamic texture packs.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
  /**
   * Shadowed {@link Minecraft#resourcePackRepository}.
   */
  @Final
  @Shadow
  private PackRepository resourcePackRepository;

  /**
   * Injects before the call to {@link PackRepository#reload()} in the constructor.<br />
   * Adds all the registered dynamic texture packs to the {@link Minecraft#resourcePackRepository}.
   *
   * @param ci {@link CallbackInfo}.
   */
  @Inject(method = "<init>", at = @At(value = "INVOKE", shift = At.Shift.BEFORE, target = "Lnet/minecraft/server/packs/repository/PackRepository;reload()V"))
  private void beforeReload(CallbackInfo ci) {
    for (Pack pack : FabricRegistryHelper.DYNAMIC_TEXTURE_PACKS) {
      resourcePackRepository.sources.add(packConsumer -> packConsumer.accept(pack));
    }
  }
}
