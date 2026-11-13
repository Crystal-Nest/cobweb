package it.crystalnest.cobweb.api.pack;

import it.crystalnest.cobweb.api.pack.generation.AssetPublicationOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Function;

/**
 * Supplies bundled or generated client assets, with optional generation for dedicated server delivery.
 */
public final class AssetPack extends ResourcePack {
  @Nullable
  private final Function<MinecraftServer, Publication> capture;

  private AssetPack(Registrar registrar) {
    super(registrar);
    capture = registrar.capture;
  }

  /**
   * Creates a registrar for a client asset pack.
   *
   * @param id logical pack identifier.
   * @return pack registrar.
   */
  public static Registrar registrar(Identifier id) {
    return new Registrar(id);
  }

  /**
   * Captures server state for the publication worker.
   *
   * @param server running server that owns the applied data.
   * @return detached publication, or null when this pack only publishes bundled assets.
   */
  @Nullable
  @ApiStatus.Internal
  public Publication capturePublication(MinecraftServer server) {
    return capture == null ? null : capture.apply(server);
  }

  /**
   * Generates assets to include in the pack offered by a dedicated server.
   *
   * @param <T> detached capture type.
   */
  @FunctionalInterface
  public interface PublicationCallback<T> {
    /**
     * Generates files for one publication on a worker thread.
     *
     * @param captured detached value produced on the server thread.
     * @param output access to this pack's bundled assets and the generated output.
     * @throws IOException if reading or writing an asset fails.
     */
    void generate(T captured, AssetPublicationOutput output) throws IOException;
  }

  /**
   * Generates assets from the state already captured for one publication.
   */
  @FunctionalInterface
  @ApiStatus.Internal
  public interface Publication {
    /**
     * Writes the generated assets for this publication.
     *
     * @param output publication output for this pack.
     * @throws IOException if an asset cannot be read or written.
     */
    void generate(AssetPublicationOutput output) throws IOException;
  }

  /**
   * Configures a client asset pack.
   */
  public static final class Registrar extends ResourcePack.Registrar<AssetPack, Registrar> {
    @Nullable
    private Function<MinecraftServer, Publication> capture;

    private Registrar(Identifier id) {
      super(id, PackType.CLIENT_RESOURCES);
    }

    /**
     * Adds generation for dedicated servers that select this pack for delivery. The capture runs on the server thread
     * after startup and successful data reloads. Return detached values that remain safe to use during later reloads.
     * The generation callback runs on a worker thread. Ordinary client generation callbacks use their native lifecycle.
     *
     * @param capture reads applied server state and returns detached inputs.
     * @param callback generates files from those inputs and the pack's bundled assets.
     * @param <T> detached capture type.
     * @return this registrar.
     * @throws IllegalStateException if publication generation was already declared.
     */
    public <T> Registrar onPublish(Function<MinecraftServer, T> capture, PublicationCallback<T> callback) {
      Objects.requireNonNull(capture);
      Objects.requireNonNull(callback);
      if (this.capture != null) {
        throw new IllegalStateException("Asset pack publication generation is already declared");
      }
      this.capture = server -> {
        T captured = capture.apply(server);
        return output -> callback.generate(captured, output);
      };
      return this;
    }

    @Override
    Registrar self() {
      return this;
    }

    @Override
    public AssetPack register() {
      return install(new AssetPack(this));
    }
  }
}
