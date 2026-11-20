package it.crystalnest.cobweb.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.crystalnest.cobweb.api.pack.generation.DynamicPackLoads;
import it.crystalnest.cobweb.platform.Services;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ReloadableServerRegistries;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Injects into {@link ReloadableServerRegistries} to append custom registries before ordinary resource listeners.
 */
@Mixin(ReloadableServerRegistries.class)
public abstract class ReloadableServerRegistriesMixin {
  /**
   * Wraps the method {@link ReloadableServerRegistries#reload}.<br>
   * Preserves native loot loading, decodes custom entries and completes the reloadable generation phase.
   *
   * @param context world and dimension registry layers.
   * @param updatedContextTags prepared native tags.
   * @param manager selected data resources.
   * @param executor native preparation executor.
   * @param original original reloadable registry loader.
   * @return completed registry context gated on generation.
   */
  @WrapMethod(method = "reload")
  private static CompletableFuture<ReloadableServerRegistries.LoadResult> wrapReload(LayeredRegistryAccess<RegistryLayer> context, List<Registry.PendingTags<?>> updatedContextTags, ResourceManager manager, Executor executor, Operation<CompletableFuture<ReloadableServerRegistries.LoadResult>> original) {
    return original.call(context, updatedContextTags, manager, executor)
      .thenCompose(result -> Services.REGISTRY.loadReloadableRegistries(result, manager, updatedContextTags, executor))
      .thenCompose(result -> DynamicPackLoads.afterReloadable(manager, result.lookupWithUpdatedTags()).thenApply(ignored -> result));
  }
}
