package it.crystalnest.cobweb.mixin;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import it.crystalnest.cobweb.api.pack.delivery.AssetDelivery;
import it.crystalnest.cobweb.api.pack.generation.DynamicPackLoads;
import it.crystalnest.cobweb.api.pack.generation.PackLoadLog;
import it.crystalnest.cobweb.api.pack.generation.lifecycle.LoadKind;
import it.crystalnest.cobweb.api.pack.selection.DataPackSelection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
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
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Injects into {@link MinecraftServer} to apply static pack activation and initial ordering.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
  /**
   * Shadowed {@link MinecraftServer#packRepository}.<br>
   * Native repository used to open the server's selected packs.
   */
  @Shadow
  @Final
  private PackRepository packRepository;

  /**
   * Generated full-load files retained for this server world.
   */
  @Unique
  private final DynamicPackLoads.FullLoadFiles fullLoadFiles = new DynamicPackLoads.FullLoadFiles();

  /**
   * Injects after the initial call to {@link MinecraftServer#buildServerStatus()} inside {@link MinecraftServer#runServer()}.<br>
   * Starts asset publication after the world and loader startup events have finished.
   *
   * @param ci callback information.
   */
  @Inject(method = "runServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;buildServerStatus()Lnet/minecraft/network/protocol/status/ServerStatus;", ordinal = 0, shift = At.Shift.AFTER))
  private void onBuildServerStatus(CallbackInfo ci) {
    if ((Object) this instanceof DedicatedServer server) {
      AssetDelivery.start(server);
    }
  }

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
  private static boolean wrapShouldAddAutomatically(PackSource instance, Operation<Boolean> original, @Local(name = "pack") Pack pack) {
    return DataPackSelection.addAutomatically(pack, original.call(instance));
  }

  /**
   * Wraps the call to {@link MinecraftServer#configureRepositoryWithSelection} inside the method {@link MinecraftServer#configurePackRepository}.<br>
   * Orders newly selected Cobweb packs while preserving saved selections and native safe mode.
   *
   * @param packRepository native pack repository.
   * @param selected IDs selected by native configuration.
   * @param forcedFeatures feature flags required by the world.
   * @param disableInactive whether inactive packs should be recorded as disabled.
   * @param original native configuration operation.
   * @param initial original world configuration.
   * @return resulting native world configuration.
   */
  @WrapOperation(method = "configurePackRepository", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;configureRepositoryWithSelection(Lnet/minecraft/server/packs/repository/PackRepository;Ljava/util/Collection;Lnet/minecraft/world/flag/FeatureFlagSet;Z)Lnet/minecraft/world/level/WorldDataConfiguration;"))
  private static WorldDataConfiguration wrapConfigureRepositoryWithSelection(PackRepository packRepository, Collection<String> selected, FeatureFlagSet forcedFeatures, boolean disableInactive, Operation<WorldDataConfiguration> original, @Local(argsOnly = true) WorldDataConfiguration initial) {
    // The safe-mode branch passes false; leave each loader's safe-mode selection untouched.
    return original.call(packRepository, disableInactive ? DataPackSelection.orderNewPacks(packRepository, selected, initial.dataPacks().getEnabled()) : selected, forcedFeatures, disableInactive);
  }

  /**
   * Modifies the pack IDs passed to the method {@link MinecraftServer#reloadResources(Collection)} at its start.<br>
   * Restores required Cobweb packs before native resource loading begins.
   *
   * @param original requested pack IDs.
   * @param shared trace and cancellation tracker for this reload.
   * @return selection including required Cobweb packs.
   */
  @ModifyVariable(method = "reloadResources", at = @At("HEAD"), argsOnly = true)
  private Collection<String> modifyPacksToEnable(Collection<String> original, @Share("generationAttempt") LocalRef<DynamicPackLoads.AttemptTracker> shared) {
    var log = new PackLoadLog(PackType.SERVER_DATA, LoadKind.RELOAD);
    shared.set(new DynamicPackLoads.AttemptTracker(log));
    return DataPackSelection.requirePacks(packRepository, original, log);
  }

  /**
   * Wraps {@link CompletableFuture#thenCompose(Function)} in {@link MinecraftServer#reloadResources(Collection)}.<br>
   * Generates early files and retains the active world's full-load output before native reload readers run.
   *
   * @param instance future opening the selected packs.
   * @param fn native reload continuation.
   * @param original original future composition.
   * @param shared shared reference to the asynchronously opened attempt.
   * @param <R> native result type.
   * @return native reload gated on generation.
   */
  @WrapOperation(method = "reloadResources", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;thenCompose(Ljava/util/function/Function;)Ljava/util/concurrent/CompletableFuture;", ordinal = 0))
  private <R> CompletableFuture<R> wrapThenCompose(CompletableFuture<ImmutableList<PackResources>> instance, Function<ImmutableList<PackResources>, CompletableFuture<R>> fn, Operation<CompletableFuture<R>> original, @Share("generationAttempt") LocalRef<DynamicPackLoads.AttemptTracker> shared) {
    MinecraftServer server = (MinecraftServer) (Object) this;
    var tracker = shared.get();
    var log = tracker.log();
    return original.call(instance, (Function<ImmutableList<PackResources>, CompletableFuture<R>>) packs -> {
      try {
        fullLoadFiles.capture(server.getResourceManager());
      } catch (RuntimeException | Error failure) {
        DynamicPackLoads.closeAfterFailure(packs, failure);
        return CompletableFuture.failedFuture(failure);
      }
      ImmutableList<PackResources> prepared;
      try {
        prepared = ImmutableList.copyOf(DynamicPackLoads.prepare(PackType.SERVER_DATA, packs, LoadKind.RELOAD, fullLoadFiles, log));
      } catch (RuntimeException | Error failure) {
        return CompletableFuture.failedFuture(failure);
      }
      DynamicPackLoads.Attempt attempt;
      try {
        attempt = DynamicPackLoads.open(PackType.SERVER_DATA, prepared, LoadKind.RELOAD, DynamicPackLoads.worldCache(server.getWorldPath(LevelResource.ROOT)), server.getPackRepository(), log);
      } catch (RuntimeException | Error failure) {
        DynamicPackLoads.closeAfterFailure(prepared, failure);
        return CompletableFuture.failedFuture(failure);
      }
      tracker.bind(attempt);
      return attempt.begin(server.registryAccess(), server.getWorldData().enabledFeatures()).thenCompose(ignored -> {
        log.handoff();
        return fn.apply(prepared);
      }).whenComplete((result, failure) -> {
        if (failure != null && attempt.hasRegisteredPacks() && !attempt.closed()) {
          DynamicPackLoads.closeAfterFailure(prepared, failure);
        }
      });
    });
  }

  /**
   * Injects at the return of {@link MinecraftServer#reloadResources(Collection)}.<br>
   * Reports the result after native application finishes and schedules asset publication after success.
   *
   * @param cir native reload future.
   * @param shared reference to the generation attempt, if opened.
   */
  @Inject(method = "reloadResources", at = @At("RETURN"), cancellable = true)
  private void onReloadResources(CallbackInfoReturnable<CompletableFuture<Void>> cir, @Share("generationAttempt") LocalRef<DynamicPackLoads.AttemptTracker> shared) {
    var tracker = shared.get();
    var server = (MinecraftServer) (Object) this;
    Runnable applied = () -> AssetDelivery.reload(server);
    var reload = tracker == null ? cir.getReturnValue().thenRun(applied) : tracker.watch(cir.getReturnValue(), applied);
    cir.setReturnValue(reload);
  }

  /**
   * Injects at the return of the method {@link MinecraftServer#stopServer()}.<br>
   * Releases the full-load output and closes dedicated asset delivery.
   *
   * @param ci callback information.
   */
  @Inject(method = "stopServer", at = @At("RETURN"))
  private void onStopServer(CallbackInfo ci) {
    AssetDelivery.stop((MinecraftServer) (Object) this);
    fullLoadFiles.close();
  }
}
