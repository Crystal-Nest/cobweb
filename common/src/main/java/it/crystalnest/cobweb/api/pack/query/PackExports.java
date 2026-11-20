package it.crystalnest.cobweb.api.pack.query;

import it.crystalnest.cobweb.api.pack.generation.GeneratedPackExport;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Writes inspection copies of currently applied Cobweb packs.
 */
public final class PackExports {
  private PackExports() {}

  /**
   * Exports one applied Cobweb pack to a new directory beneath {@code cobweb/exports}.
   * Call from the native client or server thread that owns the target. File copying runs in the background.
   *
   * @param target live client or server pack queries.
   * @param reference logical or native pack identity.
   * @return directory of the completed export, or a failed stage if the target, pack or storage is unavailable.
   */
  public static CompletionStage<Path> export(PackQueries target, PackReference reference) {
    var live = target.target();
    if (live != null) {
      return GeneratedPackExport.write(live.resources().get(), live.type(), reference);
    }
    return CompletableFuture.failedFuture(new IllegalStateException("An applied pack export needs a live target"));
  }
}
