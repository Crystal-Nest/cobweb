package it.crystalnest.cobweb.mixin;

import it.crystalnest.cobweb.api.pack.PackQueries;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects into {@link PackRepository} to refresh cached discovery metadata.
 */
@Mixin(PackRepository.class)
public abstract class PackRepositoryDiscoveryMixin {
  /**
   * Injects at the start of the method {@link PackRepository#reload()}.<br>
   * Invalidates identities while keeping requested and applied selection observations intact.
   *
   * @param ci callback information.
   */
  @Inject(method = "reload", at = @At("HEAD"))
  private void onReload(CallbackInfo ci) {
    PackQueries.invalidateDiscovery();
  }
}
