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
   * Modifies the return value of all overloads of the method {@link ServerPacksSource#createPackRepository}.<br>
   * Registers all dynamic and static data packs to the original repository.
   *
   * @param repository original return value.
   * @return updated repository.
   */
  @ModifyReturnValue(method = "createPackRepository*", at = @At(value = "RETURN"))
  private static PackRepository onCreatePackRepository(PackRepository repository) {
    return FabricRegistryHelper.registerDataPacks(repository);
  }
}
