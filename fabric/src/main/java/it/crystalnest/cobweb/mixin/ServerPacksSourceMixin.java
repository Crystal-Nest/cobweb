package it.crystalnest.cobweb.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import it.crystalnest.cobweb.platform.FabricRegistryHelper;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Injects into {@link ServerPacksSource} to add dynamic and static data packs.
 */
@Mixin(ServerPacksSource.class)
public abstract class ServerPacksSourceMixin {
  /**
   * Modifies the returned {@link PackRepository} at the end of each overload of {@link ServerPacksSource#createPackRepository}.<br>
   * Registers dynamic and static data packs in the native repository.
   *
   * @param original original return value.
   * @return updated repository.
   */
  @ModifyReturnValue(method = "createPackRepository*", at = @At(value = "RETURN"))
  private static PackRepository modifyCreatePackRepository(PackRepository original) {
    return FabricRegistryHelper.registerDataPacks(original);
  }
}
