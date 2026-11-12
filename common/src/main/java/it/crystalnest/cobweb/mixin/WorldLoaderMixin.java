package it.crystalnest.cobweb.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.util.Pair;
import it.crystalnest.cobweb.api.pack.DynamicPackLoads;
import it.crystalnest.cobweb.api.pack.LoadKind;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.world.level.WorldDataConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * Injects into {@link WorldLoader} to generate data before the native full-load readers.
 */
@Mixin(WorldLoader.class)
public abstract class WorldLoaderMixin {
  /**
   * Wraps the first {@link CompletableFuture#thenComposeAsync(Function, Executor)} in {@link WorldLoader#load}.<br>
   * Associates one generation attempt with the selected stack and cleans up early failures.
   *
   * @param instance future opening the selected packs.
   * @param continuation native full-load continuation.
   * @param executor native background executor.
   * @param original original future composition.
   * @param config world-loading configuration.
   * @param <R> native result type.
   * @return native load gated on generated content.
   */
  @WrapOperation(method = "load", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;thenComposeAsync(Ljava/util/function/Function;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
  private static <R> CompletableFuture<R> wrapThenComposeAsync(CompletableFuture<Pair<WorldDataConfiguration, CloseableResourceManager>> instance,
    Function<Pair<WorldDataConfiguration, CloseableResourceManager>, CompletableFuture<R>> continuation, Executor executor, Operation<CompletableFuture<R>> original,
    @Local(argsOnly = true) WorldLoader.InitConfig config) {
    var tracker = new DynamicPackLoads.AttemptTracker();
    CompletableFuture<R> result = original.call(instance, (Function<Pair<WorldDataConfiguration, CloseableResourceManager>, CompletableFuture<R>>) opened -> {
      var manager = opened.getSecond();
      DynamicPackLoads.Attempt attempt;
      try {
        var repository = config.packConfig().packRepository();
        attempt = DynamicPackLoads.open(PackType.SERVER_DATA, manager.listPacks().toList(), LoadKind.FULL_LOAD, DynamicPackLoads.worldCache(repository), repository);
      } catch (RuntimeException | Error failure) {
        DynamicPackLoads.closeAfterFailure(manager::close, failure);
        return CompletableFuture.failedFuture(failure);
      }
      tracker.bind(attempt);
      return attempt.begin(RegistryLayer.createRegistryAccess().getLayer(RegistryLayer.STATIC), opened.getFirst().enabledFeatures()).thenCompose(ignored -> continuation.apply(opened)).whenComplete((value, failure) -> {
        if (failure != null && attempt.hasRegisteredPacks() && !attempt.closed()) {
          DynamicPackLoads.closeAfterFailure(manager::close, failure);
        }
      });
    }, executor);
    return tracker.watch(result);
  }
}
