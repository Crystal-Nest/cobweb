package it.crystalnest.cobweb.api.pack.fixed;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;

/**
 * Client assets (textures, models, sounds, translations, etc.) bundled under {@code texturepack/<identifier path>}.
 */
public final class StaticAssetPack extends StaticResourcePack {
  /**
   * Captures the configured asset-pack settings.
   *
   * @param builder configured asset-pack builder.
   */
  private StaticAssetPack(Builder builder) {
    super(builder, PackType.CLIENT_RESOURCES);
  }

  /**
   * Creates a new typed pack builder.
   *
   * @param identifier owning mod namespace and pack path.
   * @return a new asset-pack builder.
   */
  public static Builder builder(Identifier identifier) {
    return new Builder(identifier);
  }

  /**
   * Typed asset-pack builder; safe to reference from common initialization code.
   */
  public static final class Builder extends StaticResourcePack.Builder<StaticAssetPack, Builder> {
    /**
     * Initializes a builder for bundled client assets.
     *
     * @param identifier owning mod namespace and pack path.
     */
    private Builder(Identifier identifier) {
      super(identifier, PackType.CLIENT_RESOURCES);
    }

    /**
     * Returns this asset-pack builder.
     *
     * @return this builder.
     */
    @Override
    protected Builder self() {
      return this;
    }

    /**
     * Creates an asset-pack definition without loading or registering its resources.
     *
     * @return immutable asset-pack definition.
     */
    @Override
    public StaticAssetPack build() {
      return new StaticAssetPack(this);
    }
  }
}
