package it.crystalnest.cobweb.api.pack;

import it.crystalnest.cobweb.api.pack.generation.AssetPublicationOutput;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Provides the server publication code with access to an asset pack's captured generation job.
 */
@ApiStatus.Internal
public final class AssetPackPublication {
  private AssetPackPublication() {}

  /**
   * Captures the inputs for an asset pack's publication on the server thread.
   *
   * @param pack asset pack selected for publication.
   * @param server running server whose applied data is available to the capture callback.
   * @return publication job bound to detached inputs, or null if the pack has no publication callback.
   */
  @Nullable
  public static Publication capture(AssetPack pack, MinecraftServer server) {
    return pack.capturePublication(server);
  }

  /**
   * Generates assets from the inputs already captured for one publication.
   */
  @FunctionalInterface
  public interface Publication {
    /**
     * Writes the generated assets for this publication on the publication worker.
     *
     * @param output publication output for this pack.
     * @throws IOException if an asset cannot be read or written.
     */
    void generate(AssetPublicationOutput output) throws IOException;
  }
}
