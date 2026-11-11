package it.crystalnest.cobweb.impl.pack.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import it.crystalnest.cobweb.Constants;
import it.crystalnest.cobweb.api.pack.PackExports;
import it.crystalnest.cobweb.api.pack.PackQueries;
import it.crystalnest.cobweb.api.pack.PackReference;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.function.BiConsumer;

/**
 * Registers a client-local export command for the current applied asset packs.
 */
public final class ClientPackExportCommand {
  private ClientPackExportCommand() {}

  /**
   * Adds the client command to the loader's dispatcher.
   *
   * @param dispatcher client command dispatcher.
   * @param success feedback consumer.
   * @param failure error consumer.
   * @param <S> loader's command source type.
   */
  public static <S> void register(CommandDispatcher<S> dispatcher, BiConsumer<S, Component> success, BiConsumer<S, Component> failure) {
    dispatcher.register(LiteralArgumentBuilder.<S>literal("cobweb-export-assets").then(RequiredArgumentBuilder.<S, Identifier>argument("pack", IdentifierArgument.id()).executes(context -> {
      var source = context.getSource();
      var id = context.getArgument("pack", Identifier.class);
      PackExports.export(PackQueries.client(), PackReference.logical(id)).whenComplete((directory, error) -> Minecraft.getInstance().execute(() -> {
        if (error == null) {
          success.accept(source, Component.translatable("commands.cobweb.export.success", directory.getFileName().toString()));
        } else {
          Constants.LOGGER.warn("Unable to export {}", id, error);
          failure.accept(source, Component.translatable("commands.cobweb.export.failed", id.toString()));
        }
      }));
      return 1;
    })));
  }
}
