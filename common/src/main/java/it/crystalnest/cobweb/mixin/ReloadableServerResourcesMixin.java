package it.crystalnest.cobweb.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.crystalnest.cobweb.api.pack.generation.DynamicPackLoads;
import net.minecraft.commands.Commands;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.flag.FeatureFlagSet;
import org.spongepowered.asm.mixin.Mixin;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Injects into {@link ReloadableServerResources} to generate files before reloadable registry decoding.
 */
@Mixin(ReloadableServerResources.class)
public abstract class ReloadableServerResourcesMixin {
  /**
   * Wraps the method {@link ReloadableServerResources#loadResources}.<br>
   * Exposes the completed world context and staged tags before the native reader.
   *
   * @param resourceManager selected resources.
   * @param contextLayers world and dimension registry layers.
   * @param updatedContextTags prepared tags.
   * @param enabledFeatures enabled features.
   * @param commandSelection command selection.
   * @param functionCompilationPermissions function permissions.
   * @param backgroundExecutor native preparation executor.
   * @param mainThreadExecutor native application executor.
   * @param original native loading operation.
   * @return native reload gated on generated resources.
   */
  @WrapMethod(method = "loadResources")
  private static CompletableFuture<ReloadableServerResources> wrapLoadResources(ResourceManager resourceManager, LayeredRegistryAccess<RegistryLayer> contextLayers, List<Registry.PendingTags<?>> updatedContextTags, FeatureFlagSet enabledFeatures, Commands.CommandSelection commandSelection, PermissionSet functionCompilationPermissions, Executor backgroundExecutor, Executor mainThreadExecutor, Operation<CompletableFuture<ReloadableServerResources>> original) {
    return DynamicPackLoads.beforeReloadable(resourceManager, contextLayers, updatedContextTags)
      .thenCompose(ignored -> original.call(resourceManager, contextLayers, updatedContextTags, enabledFeatures, commandSelection, functionCompilationPermissions, backgroundExecutor, mainThreadExecutor));
  }
}
