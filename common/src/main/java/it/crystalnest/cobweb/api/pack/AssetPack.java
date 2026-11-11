package it.crystalnest.cobweb.api.pack;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;

/**
 * Supplies bundled or generated client assets during native resource loading.
 */
public final class AssetPack extends ResourcePack {
  private AssetPack(Registrar registrar) {
    super(registrar);
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
   * Configures a client asset pack.
   */
  public static final class Registrar extends ResourcePack.Registrar<AssetPack, Registrar> {
    private Registrar(Identifier id) {
      super(id, PackType.CLIENT_RESOURCES);
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
