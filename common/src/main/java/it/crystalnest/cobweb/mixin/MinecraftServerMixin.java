package it.crystalnest.cobweb.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import it.crystalnest.cobweb.api.pack.DynamicPackLoads;
import it.crystalnest.cobweb.api.pack.LoadKind;
import it.crystalnest.cobweb.impl.pack.StaticPackSelection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.storage.LevelResource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Injects into {@link MinecraftServer} to apply static pack activation and initial ordering.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
  /**
   * Native repository used to open the server's selected packs.
   */
  @Shadow
  @Final
  private PackRepository packRepository;

  @Unique
  private final DynamicPackLoads.FullLoadFiles fullLoadFiles = new DynamicPackLoads.FullLoadFiles();

  /**
   * Wraps the call to {@link PackSource#shouldAddAutomatically()} inside the method {@link MinecraftServer#configurePackRepository}.<br>
   * Applies registered activation modes without replacing native pack sources.
   *
   * @param instance original native source.
   * @param original native automatic-selection operation.
   * @param pack pack currently being considered.
   * @return whether the pack should be enabled automatically.
   */
  @WrapOperation(method = "configurePackRepository", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/packs/repository/PackSource;shouldAddAutomatically()Z"))
  private static boolean wrapShouldAddAutomatically(PackSource instance, Operation<Boolean> original, @Local Pack pack) {
    return StaticPackSelection.addAutomatically(pack, original.call(instance));
  }

  /**
   * Wraps the call to {@link MinecraftServer#configureRepositoryWithSelection} inside the method {@link MinecraftServer#configurePackRepository}.<br>
   * Orders newly selected Cobweb packs while preserving saved selections and native safe mode.
   *
   * @param repository native pack repository.
   * @param selected IDs selected by native configuration.
   * @param features feature flags required by the world.
   * @param disableInactive whether inactive packs should be recorded as disabled.
   * @param original native configuration operation.
   * @param initial original world configuration.
   * @return resulting native world configuration.
   */
  @WrapOperation(method = "configurePackRepository", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;configureRepositoryWithSelection(Lnet/minecraft/server/packs/repository/PackRepository;Ljava/util/Collection;Lnet/minecraft/world/flag/FeatureFlagSet;Z)Lnet/minecraft/world/level/WorldDataConfiguration;"))
  private static WorldDataConfiguration wrapConfigureRepositoryWithSelection(PackRepository repository, Collection<String> selected, FeatureFlagSet features, boolean disableInactive,
    Operation<WorldDataConfiguration> original, @Local(argsOnly = true) WorldDataConfiguration initial) {
    // The safe-mode branch passes false; leave each loader's safe-mode selection untouched.
    return original.call(repository, disableInactive ? StaticPackSelection.orderNewPacks(repository, selected, initial.dataPacks().getEnabled()) : selected, features, disableInactive);
  }

  /**
   * Modifies the pack IDs passed to the method {@link MinecraftServer#reloadResources(Collection)} at its start.<br>
   * Restores required Cobweb packs before native resource loading begins.
   *
   * @param original requested pack IDs.
   * @return selection including required Cobweb packs.
   */
  @ModifyVariable(method = "reloadResources", at = @At("HEAD"), argsOnly = true)
  private Collection<String> modifyPacksToEnable(Collection<String> original) {
    return StaticPackSelection.requirePacks(packRepository, original);
  }

  /**
   * Wraps {@link CompletableFuture#thenCompose(Function)} in {@link MinecraftServer#reloadResources(Collection)}.<br>
   * Generates early files and retains the active world's full-load output before native reload readers run.
   *
   * @param instance future opening the selected packs.
   * @param continuation native reload continuation.
   * @param original original future composition.
   * @param shared shared reference to the asynchronously opened attempt.
   * @param <R> native result type.
   * @return native reload gated on generation.
   */
  @WrapOperation(method = "reloadResources", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;thenCompose(Ljava/util/function/Function;)Ljava/util/concurrent/CompletableFuture;", ordinal = 0))
  private <R> CompletableFuture<R> wrapThenCompose(CompletableFuture<List<PackResources>> instance, Function<List<PackResources>, CompletableFuture<R>> continuation, Operation<CompletableFuture<R>> original,
    @Share("generationAttempt") LocalRef<DynamicPackLoads.AttemptTracker> shared) {
    MinecraftServer server = (MinecraftServer) (Object) this;
    var tracker = new DynamicPackLoads.AttemptTracker();
    shared.set(tracker);
    return original.call(instance, (Function<List<PackResources>, CompletableFuture<R>>) packs -> {
      DynamicPackLoads.Attempt attempt;
      try {
        fullLoadFiles.capture(server.getResourceManager());
        attempt = DynamicPackLoads.open(PackType.SERVER_DATA, packs, LoadKind.RELOAD, fullLoadFiles, server.getWorldPath(LevelResource.ROOT).resolve("cobweb/cache/data"), server.getPackRepository());
      } catch (RuntimeException | Error failure) {
        packs.forEach(PackResources::close);
        return CompletableFuture.failedFuture(failure);
      }
      tracker.bind(attempt);
      return attempt.begin(server.registryAccess(), server.getWorldData().enabledFeatures()).thenCompose(ignored -> continuation.apply(packs)).whenComplete((result, failure) -> {
        if (failure != null && attempt.hasRegisteredPacks() && !attempt.closed()) {
          packs.forEach(PackResources::close);
        }
      });
    });
  }

  /**
   * Injects at the return of {@link MinecraftServer#reloadResources(Collection)}.<br>
   * Reports the result after native application finishes.
   *
   * @param cir native reload future.
   * @param shared reference to the generation attempt, if opened.
   */
  @Inject(method = "reloadResources", at = @At("RETURN"), cancellable = true)
  private void onReloadResources(CallbackInfoReturnable<CompletableFuture<Void>> cir, @Share("generationAttempt") LocalRef<DynamicPackLoads.AttemptTracker> shared) {
    var tracker = shared.get();
    if (tracker != null) {
      cir.setReturnValue(tracker.watch(cir.getReturnValue()));
    }
  }

  /**
   * Injects at the return of the method {@link MinecraftServer#stopServer()}.<br>
   * Releases the full-load output retained for this world.
   *
   * @param ci callback information.
   */
  @Inject(method = "stopServer", at = @At("RETURN"))
  private void onStopServer(CallbackInfo ci) {
    fullLoadFiles.close();
  }
}
