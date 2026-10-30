package it.crystalnest.cobweb.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import it.crystalnest.cobweb.impl.pack.StaticPackSelection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.WorldDataConfiguration;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Collection;

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
}
