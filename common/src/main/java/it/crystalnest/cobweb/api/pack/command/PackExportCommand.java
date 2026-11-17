package it.crystalnest.cobweb.api.pack.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import it.crystalnest.cobweb.Constants;
import it.crystalnest.cobweb.api.pack.query.PackExports;
import it.crystalnest.cobweb.api.pack.query.PackQueries;
import it.crystalnest.cobweb.api.pack.query.PackReference;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.players.NameAndId;
import org.jetbrains.annotations.ApiStatus;

import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Registers inspection export commands for applied server data packs and client asset packs.
 */
@ApiStatus.Internal
public final class PackExportCommand {
  private PackExportCommand() {}

  /**
   * Adds a command available to dedicated-server administrators and the local world owner.
   *
   * @param dispatcher native command dispatcher.
   */
  public static void registerServer(CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(Commands.literal("cobweb").then(Commands.literal("export").requires(PackExportCommand::canExport).then(Commands.argument("pack", IdentifierArgument.id()).executes(context -> {
      var source = context.getSource();
      var server = source.getServer();
      var pack = PackReference.logical(IdentifierArgument.getId(context, "pack"));
      return export(PackQueries.forServer(server), pack, server, message -> source.sendSuccess(() -> message, false), source::sendFailure);
    }))));
  }

  /**
   * Adds the client-local command to the loader's dispatcher.
   *
   * @param dispatcher client command dispatcher.
   * @param clientThread executor for feedback on the client thread.
   * @param success success feedback consumer.
   * @param failure error feedback consumer.
   * @param <S> loader's command source type.
   */
  public static <S> void registerClient(CommandDispatcher<S> dispatcher, Executor clientThread, BiConsumer<S, Component> success, BiConsumer<S, Component> failure) {
    dispatcher.register(LiteralArgumentBuilder.<S>literal("cobweb-export-assets").then(RequiredArgumentBuilder.<S, Identifier>argument("pack", IdentifierArgument.id()).executes(context -> {
      var source = context.getSource();
      var pack = PackReference.logical(context.getArgument("pack", Identifier.class));
      return export(PackQueries.client(), pack, clientThread, message -> success.accept(source, message), message -> failure.accept(source, message));
    })));
  }

  /**
   * Starts an export and reports the result on the owning game thread.
   *
   * @param target live pack query target.
   * @param pack logical reference created from the command argument.
   * @param feedbackThread executor for feedback on the owning game thread.
   * @param success receiver for the completed export message.
   * @param failure receiver for the error message.
   * @return Brigadier's success value for accepting the export request.
   */
  private static int export(PackQueries target, PackReference.Logical pack, Executor feedbackThread, Consumer<Component> success, Consumer<Component> failure) {
    PackExports.export(target, pack).whenComplete((directory, error) -> feedbackThread.execute(() -> {
      if (error == null) {
        success.accept(Component.translatable("commands.cobweb.export.success", directory.getFileName().toString()));
      } else {
        Constants.LOGGER.error("Unable to export {}", pack.id(), error);
        failure.accept(Component.translatable("commands.cobweb.export.failed", pack.id().toString()));
      }
    }));
    return 1;
  }

  /**
   * Checks whether the source is an administrator or the local world owner.
   *
   * @param source source requesting access to the export command.
   * @return whether the source can export data packs.
   */
  private static boolean canExport(CommandSourceStack source) {
    var player = source.getPlayer();
    var server = source.getServer();
    return Commands.<CommandSourceStack>hasPermission(Commands.LEVEL_ADMINS).test(source) || player != null && !server.isDedicatedServer() && server.isSingleplayerOwner(new NameAndId(player.getGameProfile()));
  }
}
