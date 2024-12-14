package it.crystalnest.cobweb.mixin.client;

import it.crystalnest.cobweb.platform.FabricRegistryHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldCallback;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContextMapper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.function.Function;

/**
 * Injects into {@link CreateWorldScreen} to add dynamic and static data packs.
 */
@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin {
  /**
   * Modifies the {@link PackRepository} variable in the method {@link CreateWorldScreen#openCreateWorldScreen(Minecraft, Screen, Function, WorldCreationContextMapper, ResourceKey, CreateWorldCallback)}.<br>
   * Registers all dynamic and static data packs to the original repository.
   *
   * @param repository original {@link PackRepository}.
   * @return updated repository.
   */
  @ModifyVariable(method = "openCreateWorldScreen", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/worldselection/CreateWorldScreen;createDefaultLoadConfig(Lnet/minecraft/server/packs/repository/PackRepository;Lnet/minecraft/world/level/WorldDataConfiguration;)Lnet/minecraft/server/WorldLoader$InitConfig;"))
  private static PackRepository onOpenCreateWorldScreen(PackRepository repository) {
    return FabricRegistryHelper.registerDataPacks(repository);
  }
}
