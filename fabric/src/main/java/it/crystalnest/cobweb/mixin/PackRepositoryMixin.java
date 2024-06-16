package it.crystalnest.cobweb.mixin;

import it.crystalnest.cobweb.platform.FabricRegistryHelper;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Injects into {@link PackRepository} to add dynamic data packs.
 */
@Mixin(PackRepository.class)
public abstract class PackRepositoryMixin {
  /**
   * Modifies the {@link RepositorySource} argument for the {@link PackRepository} constructor in the constructor.<br />
   * Adds all the registered dynamic data packs to the {@link PackRepository#sources}.
   *
   * @param sources original {@link RepositorySource}s.
   * @return modified {@link RepositorySource}s.
   */
  @ModifyArg(method = "<init>(Lnet/minecraft/server/packs/PackType;[Lnet/minecraft/server/packs/repository/RepositorySource;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/packs/repository/PackRepository;<init>(Lnet/minecraft/server/packs/repository/Pack$PackConstructor;[Lnet/minecraft/server/packs/repository/RepositorySource;)V"), index = 1)
  private static RepositorySource[] onInit(RepositorySource[] sources) {
    RepositorySource[] providers = new RepositorySource[sources.length + FabricRegistryHelper.DYNAMIC_DATA_PACKS.size()];
    System.arraycopy(sources, 0, providers, 0, sources.length);
    for (int i = 0; i < FabricRegistryHelper.DYNAMIC_DATA_PACKS.size(); i++) {
      Pack pack = FabricRegistryHelper.DYNAMIC_DATA_PACKS.get(i).get();
      providers[sources.length + i] = (consumer, factory) -> consumer.accept(pack);
    }
    return providers;
  }
}
