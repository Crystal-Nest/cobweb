package it.crystalnest.cobweb.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.crystalnest.cobweb.api.pack.generation.DynamicPackLoads;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Injects into {@link RegistryDataLoader} to gate native decoding on generation.
 */
@Mixin(RegistryDataLoader.class)
public abstract class RegistryDataLoaderMixin {
  /**
   * Wraps the NeoForge condition-aware overload of {@link RegistryDataLoader#load(ResourceManager, List, List, Executor, List)}.<br>
   * Runs generation before world entry decoding and after completed registry batches.
   *
   * @param resourceManager selected resources.
   * @param contextRegistries native input lookups.
   * @param registriesToLoad native batch definitions.
   * @param executor native executor.
   * @param pendingTags staged native tags supplied to NeoForge's resource conditions.
   * @param original original registry loader.
   * @return native batch gated on the associated generation phases.
   */
  @WrapMethod(method = "load(Lnet/minecraft/server/packs/resources/ResourceManager;Ljava/util/List;Ljava/util/List;Ljava/util/concurrent/Executor;Ljava/util/List;)Ljava/util/concurrent/CompletableFuture;")
  private static CompletableFuture<RegistryAccess.Frozen> wrapLoadWithConditions(ResourceManager resourceManager, List<HolderLookup.RegistryLookup<?>> contextRegistries, List<RegistryDataLoader.RegistryData<?>> registriesToLoad, Executor executor, List<Registry.PendingTags<?>> pendingTags, Operation<CompletableFuture<RegistryAccess.Frozen>> original) {
    return DynamicPackLoads.beforeBatch(resourceManager, contextRegistries, registriesToLoad)
      .thenCompose(ignored -> original.call(resourceManager, contextRegistries, registriesToLoad, executor, pendingTags))
      .thenCompose(decoded -> DynamicPackLoads.afterBatch(resourceManager, contextRegistries, registriesToLoad, decoded));
  }
}
