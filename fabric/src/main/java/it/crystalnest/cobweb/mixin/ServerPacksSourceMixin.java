package it.crystalnest.cobweb.mixin;

import it.crystalnest.cobweb.platform.FabricRegistryHelper;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.world.level.validation.DirectoryValidator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.nio.file.Path;

/**
 * Injects into {@link ServerPacksSource} to add dynamic data packs.
 */
@Mixin(ServerPacksSource.class)
public abstract class ServerPacksSourceMixin {
  /**
   * Modifies the {@link RepositorySource} argument for the {@link PackRepository} constructor in the method {@link ServerPacksSource#createPackRepository(Path, DirectoryValidator)}.<br />
   * Adds all the registered dynamic data packs to the {@link PackRepository#sources}.
   *
   * @param sources original {@link RepositorySource}s.
   * @return modified {@link RepositorySource}s.
   */
  @ModifyArg(method = "createPackRepository(Ljava/nio/file/Path;Lnet/minecraft/world/level/validation/DirectoryValidator;)Lnet/minecraft/server/packs/repository/PackRepository;", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/packs/repository/PackRepository;<init>([Lnet/minecraft/server/packs/repository/RepositorySource;)V"))
  private static RepositorySource[] onCreatePackRepository(RepositorySource[] sources) {
    RepositorySource[] providers = new RepositorySource[sources.length + FabricRegistryHelper.DYNAMIC_DATA_PACKS.size()];
    System.arraycopy(sources, 0, providers, 0, sources.length);
    for (int i = 0; i < FabricRegistryHelper.DYNAMIC_DATA_PACKS.size(); i++) {
      Pack pack = FabricRegistryHelper.DYNAMIC_DATA_PACKS.get(i);
      providers[sources.length + i] = packConsumer -> packConsumer.accept(pack);
    }
    return providers;
  }
}
