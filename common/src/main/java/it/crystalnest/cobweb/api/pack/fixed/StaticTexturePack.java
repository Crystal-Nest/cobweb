package it.crystalnest.cobweb.api.pack.fixed;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;

/**
 * Texture pack built-in within a mod.
 */
public final class StaticTexturePack extends StaticResourcePack {
  /**
   * @param location {@link #location}.
   * @param source {@link #source}.
   * @param position {@link #position}.
   * @param alwaysActive {@link #alwaysActive}.
   */
  public StaticTexturePack(ResourceLocation location, PackSource source, Pack.Position position, boolean alwaysActive) {
    super(location, PackType.CLIENT_RESOURCES, source, position, alwaysActive);
  }

  /**
   * @param location {@link #location}.
   * @param position {@link #position}.
   */
  public StaticTexturePack(ResourceLocation location, Pack.Position position) {
    super(location, PackType.CLIENT_RESOURCES, position);
  }

  @Override
  public String directory() {
    return "texturepack";
  }
}
