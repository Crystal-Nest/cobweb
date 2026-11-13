package it.crystalnest.cobweb.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import it.crystalnest.cobweb.api.pack.generation.DynamicPackLoads;
import net.minecraft.core.Registry;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Injects into {@link TagLoader} to expose prepared tag members to generation.
 */
@Mixin(TagLoader.class)
public abstract class TagLoaderMixin {
  /**
   * Wraps the call to {@link Registry#prepareTagReload(TagLoader.LoadResult)} inside {@link TagLoader#loadPendingTags}.<br>
   * Captures the native members before pending tags are applied globally.
   *
   * @param instance native registry.
   * @param result decoded tag membership.
   * @param original native tag preparation.
   * @param manager selected resources.
   * @return unchanged pending tags.
   */
  @WrapOperation(method = "loadPendingTags", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/Registry;prepareTagReload(Lnet/minecraft/tags/TagLoader$LoadResult;)Lnet/minecraft/core/Registry$PendingTags;"))
  private static Registry.PendingTags<?> wrapPrepareTagReload(Registry<?> instance, TagLoader.LoadResult<?> result, Operation<Registry.PendingTags<?>> original, @Local(argsOnly = true) ResourceManager manager) {
    DynamicPackLoads.captureTags(manager, result);
    return original.call(instance, result);
  }
}
