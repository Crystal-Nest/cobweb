package it.crystalnest.cobweb.api.pack;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;

/**
 * Supplies bundled or generated server data during native resource loading.
 */
public final class DataPack extends ResourcePack {
  /**
   * @param registrar configuration for the asset pack.
   */
  private DataPack(Registrar registrar) {
    super(registrar);
  }

  /**
   * Creates a registrar for a server data pack.
   *
   * @param id logical pack identifier.
   * @return pack registrar.
   */
  public static Registrar registrar(Identifier id) {
    return new Registrar(id);
  }

  /**
   * Configures a server data pack.
   */
  public static final class Registrar extends ResourcePack.Registrar<DataPack, Registrar> {
    /**
     * @param id logical identifier of the data pack to register.
     */
    private Registrar(Identifier id) {
      super(id, PackType.SERVER_DATA);
    }

    @Override
    Registrar self() {
      return this;
    }

    @Override
    public DataPack register() {
      return install(new DataPack(this));
    }
  }
}
