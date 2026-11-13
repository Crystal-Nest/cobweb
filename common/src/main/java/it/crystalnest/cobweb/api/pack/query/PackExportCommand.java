package it.crystalnest.cobweb.api.pack.query;

import com.mojang.brigadier.CommandDispatcher;
import it.crystalnest.cobweb.Constants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.NameAndId;
import org.jetbrains.annotations.ApiStatus;

/**
 * Registers the inspection export command for server data packs.
 */
@ApiStatus.Internal
public final class PackExportCommand {
  private PackExportCommand() {}

  /**
   * Adds a command available to dedicated-server administrators and the local world owner.
   *
   * @param dispatcher native command dispatcher.
   */
  public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(Commands.literal("cobweb").then(Commands.literal("export")
      .requires(PackExportCommand::canExport)
      .then(Commands.argument("pack", IdentifierArgument.id()).executes(context -> {
        var source = context.getSource();
        var server = source.getServer();
        var pack = PackReference.logical(IdentifierArgument.getId(context, "pack"));
        PackExports.export(PackQueries.forServer(server), pack).whenComplete((directory, failure) -> server.execute(() -> {
          if (failure == null) {
            source.sendSuccess(() -> Component.translatable("commands.cobweb.export.success", directory.getFileName().toString()), false);
          } else {
            Constants.LOGGER.warn("Unable to export {}", pack, failure);
            source.sendFailure(Component.translatable("commands.cobweb.export.failed", pack.logicalId().toString()));
          }
        }));
        return 1;
      }))));
  }

  private static boolean canExport(CommandSourceStack source) {
    var player = source.getPlayer();
    var server = source.getServer();
    return Commands.<CommandSourceStack>hasPermission(Commands.LEVEL_ADMINS).test(source)
      || player != null && server != null && !server.isDedicatedServer() && server.isSingleplayerOwner(new NameAndId(player.getGameProfile()));
  }
}
