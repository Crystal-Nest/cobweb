package it.crystalnest.cobweb.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.crystalnest.cobweb.api.pack.generation.DynamicPackLoads;
import it.crystalnest.cobweb.api.pack.generation.PackLoadLog;
import it.crystalnest.cobweb.api.pack.generation.lifecycle.LoadKind;
import it.crystalnest.cobweb.api.pack.query.PackQueryLifecycle;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.util.Unit;
import net.minecraft.world.flag.FeatureFlagSet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

/**
 * Injects into {@link ReloadableResourceManager} to supply generated client assets.
 */
@Mixin(ReloadableResourceManager.class)
public abstract class ReloadableResourceManagerMixin {
  /**
   * Shadowed {@link ReloadableResourceManager#type}.<br>
   * Native resource type managed by this reloadable resource manager.
   */
  @Shadow
  @Final
  private PackType type;

  /**
   * Whether this manager has started its first resource load.
   */
  @Unique
  private boolean started;

  /**
   * Wraps the method {@link ReloadableResourceManager#createReload}.<br>
   * Defers native preparation until the selected generated assets are complete.
   *
   * @param backgroundExecutor native preparation executor.
   * @param mainThreadExecutor native application executor.
   * @param initialTask initial native task.
   * @param resourcePacks selected native packs.
   * @param original native reload operation.
   * @return reload instance gated on generation.
   */
  @WrapMethod(method = "createReload")
  private ReloadInstance wrapCreateReload(Executor backgroundExecutor, Executor mainThreadExecutor, CompletableFuture<Unit> initialTask, List<PackResources> resourcePacks, Operation<ReloadInstance> original) {
    if (type != PackType.CLIENT_RESOURCES) {
      started = true;
      return original.call(backgroundExecutor, mainThreadExecutor, initialTask, resourcePacks);
    }
    LoadKind kind = started ? LoadKind.RELOAD : LoadKind.FULL_LOAD;
    started = true;
    var log = new PackLoadLog(type, kind);
    var tracker = new DynamicPackLoads.AttemptTracker(log);
    List<PackResources> prepared;
    try {
      prepared = DynamicPackLoads.prepare(type, resourcePacks, kind, null, log);
    } catch (RuntimeException | Error failure) {
      return DynamicPackLoads.gatedReload(CompletableFuture.failedFuture(failure), tracker);
    }
    CompletableFuture<ReloadInstance> nativeReload;
    try {
      var repository = Minecraft.getInstance().getResourcePackRepository();
      PackQueryLifecycle.bindClient(repository, (ReloadableResourceManager) (Object) this);
      var attempt = DynamicPackLoads.open(type, prepared, kind, Path.of("cobweb", "cache", "assets"), repository, log);
      tracker.bind(attempt);
      if (attempt.generates()) {
        nativeReload = attempt.begin(HolderLookup.Provider.create(Stream.empty()), FeatureFlagSet.of()).thenApplyAsync(ignored -> {
          log.handoff();
          return original.call(backgroundExecutor, mainThreadExecutor, initialTask, prepared);
        }, mainThreadExecutor);
      } else {
        log.handoff();
        nativeReload = CompletableFuture.completedFuture(original.call(backgroundExecutor, mainThreadExecutor, initialTask, prepared));
      }
    } catch (RuntimeException | Error failure) {
      nativeReload = CompletableFuture.failedFuture(failure);
    }
    nativeReload.whenComplete((reload, failure) -> {
      if (failure != null) {
        DynamicPackLoads.closeAfterFailure(prepared, failure);
      }
    });
    return DynamicPackLoads.gatedReload(nativeReload, tracker);
  }
}
