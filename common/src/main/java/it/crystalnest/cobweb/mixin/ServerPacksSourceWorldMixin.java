package it.crystalnest.cobweb.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import it.crystalnest.cobweb.api.pack.DynamicPackLoads;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Injects into {@link ServerPacksSource} to scope data pack cache records to their world.
 */
@Mixin(ServerPacksSource.class)
public abstract class ServerPacksSourceWorldMixin {
  /**
   * Modifies the returned repository in {@link ServerPacksSource#createPackRepository(LevelStorageSource.LevelStorageAccess)}.<br>
   * Records the native saved-world root for later full loading.
   *
   * @param repository native data pack repository.
   * @param access saved world storage.
   * @return original repository.
   */
  @ModifyReturnValue(method = "createPackRepository(Lnet/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess;)Lnet/minecraft/server/packs/repository/PackRepository;", at = @At("RETURN"))
  private static PackRepository modifyCreatePackRepository(PackRepository repository, LevelStorageSource.LevelStorageAccess access) {
    DynamicPackLoads.bindWorld(repository, access.getLevelPath(LevelResource.ROOT));
    return repository;
  }
}
