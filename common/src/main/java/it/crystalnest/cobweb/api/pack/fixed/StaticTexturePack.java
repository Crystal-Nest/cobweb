package it.crystalnest.cobweb.api.pack.fixed;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;

/**
 * Texture pack built-in within a mod.
 */
public final class StaticTexturePack extends StaticResourcePack {
  /**
   * @param identifier {@link #identifier}.
   * @param source {@link #source}.
   * @param position {@link #position}.
   * @param alwaysActive {@link #alwaysActive}.
   */
  public StaticTexturePack(Identifier identifier, PackSource source, Pack.Position position, boolean alwaysActive) {
    super(identifier, PackType.CLIENT_RESOURCES, source, position, alwaysActive);
  }

  /**
   * @param identifier {@link #identifier}.
   * @param position {@link #position}.
   */
  public StaticTexturePack(Identifier identifier, Pack.Position position) {
    super(identifier, PackType.CLIENT_RESOURCES, position);
  }

  @Override
  public String directory() {
    return "texturepack";
  }
}
