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
   * Wraps the native resource overload of {@link RegistryDataLoader#load}.<br>
   * Runs generation before world entry decoding and after completed registry batches.
   *
   * @param manager selected resources.
   * @param lookups native input lookups.
   * @param definitions native batch definitions.
   * @param executor native executor.
   * @param original original registry loader.
   * @return native batch gated on the associated generation phases.
   */
  @WrapMethod(method = "load(Lnet/minecraft/server/packs/resources/ResourceManager;Ljava/util/List;Ljava/util/List;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;", require = 0)
  private static CompletableFuture<RegistryAccess.Frozen> wrapLoad(ResourceManager manager, List<HolderLookup.RegistryLookup<?>> lookups, List<RegistryDataLoader.RegistryData<?>> definitions, Executor executor,
    Operation<CompletableFuture<RegistryAccess.Frozen>> original) {
    return DynamicPackLoads.beforeBatch(manager, lookups, definitions)
      .thenCompose(ignored -> original.call(manager, lookups, definitions, executor))
      .thenCompose(decoded -> DynamicPackLoads.afterBatch(manager, lookups, definitions, decoded));
  }

  /**
   * Wraps the NeoForge condition-aware overload of {@link RegistryDataLoader#load}.<br>
   * Runs generation before world entry decoding and after completed registry batches.
   *
   * @param manager selected resources.
   * @param lookups native input lookups.
   * @param definitions native batch definitions.
   * @param executor native executor.
   * @param pending staged native tags.
   * @param original original registry loader.
   * @return native batch gated on the associated generation phases.
   */
  @WrapMethod(method = "load(Lnet/minecraft/server/packs/resources/ResourceManager;Ljava/util/List;Ljava/util/List;Ljava/util/concurrent/Executor;Ljava/util/List;)Ljava/util/concurrent/CompletableFuture;", require = 0)
  private static CompletableFuture<RegistryAccess.Frozen> wrapLoadWithConditions(ResourceManager manager, List<HolderLookup.RegistryLookup<?>> lookups, List<RegistryDataLoader.RegistryData<?>> definitions,
    Executor executor, List<Registry.PendingTags<?>> pending,
    Operation<CompletableFuture<RegistryAccess.Frozen>> original) {
    return DynamicPackLoads.beforeBatch(manager, lookups, definitions)
      .thenCompose(ignored -> original.call(manager, lookups, definitions, executor, pending))
      .thenCompose(decoded -> DynamicPackLoads.afterBatch(manager, lookups, definitions, decoded));
  }
}
