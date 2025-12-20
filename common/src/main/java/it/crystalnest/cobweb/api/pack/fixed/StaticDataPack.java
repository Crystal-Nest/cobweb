package it.crystalnest.cobweb.api.pack.fixed;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;

/**
 * Data pack built-in within a mod.
 */
public final class StaticDataPack extends StaticResourcePack {
  /**
   * @param identifier {@link #identifier}.
   * @param source {@link #source}.
   * @param position {@link #position}.
   * @param alwaysActive {@link #alwaysActive}.
   */
  public StaticDataPack(Identifier identifier, PackSource source, Pack.Position position, boolean alwaysActive) {
    super(identifier, PackType.SERVER_DATA, source, position, alwaysActive);
  }

  /**
   * @param identifier {@link #identifier}.
   * @param position {@link #position}.
   */
  public StaticDataPack(Identifier identifier, Pack.Position position) {
    super(identifier, PackType.SERVER_DATA, position);
  }

  @Override
  public String directory() {
    return "datapack";
  }
}
