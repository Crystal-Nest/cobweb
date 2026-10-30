package it.crystalnest.cobweb.mixin;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import it.crystalnest.cobweb.impl.pack.StaticPackSelection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.DataPackCommand;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Injects into {@link DataPackCommand} to prevent disabling required Cobweb data packs.
 */
@Mixin(DataPackCommand.class)
public abstract class DataPackCommandMixin {
  /**
   * Localized error for attempts to disable an always-active Cobweb data pack.
   */
  @Unique
  private static final DynamicCommandExceptionType ERROR_REQUIRED_PACK = new DynamicCommandExceptionType(id -> Component.translatable("commands.cobweb.datapack.disable.required", id));

  /**
   * Injects at the start of the method {@link DataPackCommand#disablePack(CommandSourceStack, Pack)}.<br>
   * Rejects disabling always-active Cobweb packs before the native command reports success.
   *
   * @param source command source.
   * @param pack data pack being disabled.
   * @param cir native command callback.
   * @throws CommandSyntaxException if the pack is always active.
   */
  @Inject(method = "disablePack", at = @At("HEAD"))
  private static void onDisablePack(CommandSourceStack source, Pack pack, CallbackInfoReturnable<Integer> cir) throws CommandSyntaxException {
    if (StaticPackSelection.isAlwaysActive(PackType.SERVER_DATA, pack)) {
      throw ERROR_REQUIRED_PACK.create(pack.getId());
    }
  }
}
