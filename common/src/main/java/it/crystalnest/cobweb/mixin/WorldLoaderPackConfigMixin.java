package it.crystalnest.cobweb.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.crystalnest.cobweb.api.pack.generation.DynamicPackLoads;
import it.crystalnest.cobweb.api.pack.generation.PackLoadLog;
import it.crystalnest.cobweb.api.pack.generation.lifecycle.LoadKind;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/**
 * Injects into {@link WorldLoader.PackConfig} to expose file rule outputs to the initial world resource manager.
 */
@Mixin(WorldLoader.PackConfig.class)
public abstract class WorldLoaderPackConfigMixin {
  /**
   * Wraps the call to {@link PackRepository#openAllSelected()} inside {@link WorldLoader.PackConfig#createResourceManager()}.<br>
   * Adapts selected file-rule packs before Minecraft indexes their namespaces.
   *
   * @param instance native pack repository.
   * @param original native resource opening.
   * @return selected resource openings.
   */
  @WrapOperation(method = "createResourceManager", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/packs/repository/PackRepository;openAllSelected()Ljava/util/List;"))
  private List<PackResources> wrapOpenAllSelected(PackRepository instance, Operation<List<PackResources>> original) {
    return DynamicPackLoads.prepare(PackType.SERVER_DATA, original.call(instance), LoadKind.FULL_LOAD, null, PackLoadLog.fullLoad());
  }
}
