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
   * @param manager selected resources.
   * @param layers world and dimension registry layers.
   * @param tags prepared tags.
   * @param features enabled features.
   * @param commands command selection.
   * @param permissions function permissions.
   * @param background native preparation executor.
   * @param main native application executor.
   * @param original native loading operation.
   * @return native reload gated on generated resources.
   */
  @WrapMethod(method = "loadResources")
  private static CompletableFuture<ReloadableServerResources> wrapLoadResources(ResourceManager manager, LayeredRegistryAccess<RegistryLayer> layers, List<Registry.PendingTags<?>> tags, FeatureFlagSet features,
    Commands.CommandSelection commands, PermissionSet permissions, Executor background, Executor main, Operation<CompletableFuture<ReloadableServerResources>> original) {
    return DynamicPackLoads.beforeReloadable(manager, layers, tags).thenCompose(ignored -> original.call(manager, layers, tags, features, commands, permissions, background, main));
  }
}
