package it.crystalnest.cobweb.api.pack.fixed;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;

/**
 * Data pack built-in within a mod.
 */
public final class StaticDataPack extends StaticResourcePack {
  /**
   * @param location {@link #location}.
   * @param source {@link #source}.
   * @param position {@link #position}.
   * @param alwaysActive {@link #alwaysActive}.
   */
  public StaticDataPack(ResourceLocation location, PackSource source, Pack.Position position, boolean alwaysActive) {
    super(location, PackType.SERVER_DATA, source, position, alwaysActive);
  }

  /**
   * @param location {@link #location}.
   * @param position {@link #position}.
   */
  public StaticDataPack(ResourceLocation location, Pack.Position position) {
    super(location, PackType.SERVER_DATA, position);
  }

  @Override
  public String directory() {
    return "datapack";
  }
}
