package it.crystalnest.cobweb.api.pack;

import it.crystalnest.cobweb.api.pack.generation.AssetPublicationOutput;
import it.crystalnest.cobweb.platform.services.PackHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Function;

/**
 * Supplies bundled or generated client assets.
 * <p>
 * A dedicated server can publish the bundled assets and any output from {@link Registrar#onPublish(Function, PublicationCallback)} when the operator selects this pack for delivery.
 */
public final class AssetPack extends ResourcePack {
  /**
   * @param registrar configuration for the asset pack.
   */
  private AssetPack(Registrar registrar) {
    super(registrar);
  }

  /**
   * Creates a registrar for a client asset pack.
   *
   * @param id logical pack identifier whose namespace identifies the owning mod.
   * @return a new registrar for the asset pack.
   * @throws NullPointerException if the identifier is null.
   */
  public static Registrar registrar(Identifier id) {
    return new Registrar(id);
  }

  /**
   * Generates assets for a dedicated server's published pack from detached inputs.
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
   * Configures a client asset pack.
   */
  public static final class Registrar extends ResourcePack.Registrar<AssetPack, Registrar> {
    /**
     * Optional server-state capture and worker callback configured for this pack.
     */
    @Nullable
    private PackHelper.PublicationCallbacks<?> publication;

    /**
     * @param id logical identifier of the asset pack to register.
     */
    private Registrar(Identifier id) {
      super(id, PackType.CLIENT_RESOURCES);
    }

    /**
     * Declares generation for dedicated servers whose operator selects this pack for delivery.
     * <p>
     * Cobweb runs the capture on the server thread after startup and successful data reloads.
     * Return detached inputs that remain safe to use during later reloads. The generation callback runs on a worker thread.
     * Ordinary client generation callbacks follow the client resource lifecycle.
     *
     * @param capture reads applied server state and returns detached inputs for the worker.
     * @param callback generates files from the captured inputs and the pack's bundled assets.
     * @param <T> detached capture type.
     * @return this registrar.
     * @throws NullPointerException if either callback is null.
     * @throws IllegalStateException if publication generation was already declared.
     */
    public <T> Registrar onPublish(Function<MinecraftServer, T> capture, PublicationCallback<T> callback) {
      Objects.requireNonNull(capture);
      Objects.requireNonNull(callback);
      if (publication != null) {
        throw new IllegalStateException("Asset pack publication generation is already declared");
      }
      publication = new PackHelper.PublicationCallbacks<>(capture, callback);
      return this;
    }

    @Override
    Registrar self() {
      return this;
    }

    @Override
    public AssetPack register() {
      return install(new AssetPack(this), publication);
    }
  }
}
