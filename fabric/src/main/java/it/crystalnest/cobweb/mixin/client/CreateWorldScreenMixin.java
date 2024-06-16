package it.crystalnest.cobweb.mixin.client;

import it.crystalnest.cobweb.platform.FabricRegistryHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.function.Supplier;

/**
 * Injects into {@link CreateWorldScreen} to add dynamic data packs.
 */
@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin {
  /**
   * Modifies the {@link PackRepository} variable in the method {@link CreateWorldScreen#openFresh(Minecraft, Screen)}.<br />
   * Changes the {@link PackRepository#sources} adding all registered dynamic data packs.
   *
   * @param packRepository {@link PackRepository}.
   * @return modified {@link PackRepository} with added sources.
   */
  @ModifyVariable(method = "openFresh", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/worldselection/CreateWorldScreen;createDefaultLoadConfig(Lnet/minecraft/server/packs/repository/PackRepository;Lnet/minecraft/world/level/DataPackConfig;)Lnet/minecraft/server/WorldLoader$InitConfig;"))
  private static PackRepository onCreate(PackRepository packRepository) {
    for (Supplier<Pack> pack : FabricRegistryHelper.DYNAMIC_DATA_PACKS) {
      packRepository.sources.add((consumer, factory) -> consumer.accept(pack.get()));
    }
    return packRepository;
  }
}
