package it.crystalnest.cobweb.api.pack.fixed;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;

/**
 * Server data bundled under {@code datapack/<identifier path>} in the owning mod.
 */
public final class StaticDataPack extends StaticResourcePack {
  /**
   * Captures the configured data-pack settings.
   *
   * @param builder configured data-pack builder.
   */
  private StaticDataPack(Builder builder) {
    super(builder, PackType.SERVER_DATA);
  }

  /**
   * Creates a new typed pack builder.
   *
   * @param identifier owning mod namespace and pack path.
   * @return a new data-pack builder.
   */
  public static Builder builder(Identifier identifier) {
    return new Builder(identifier);
  }

  /**
   * Typed data-pack builder.
   */
  public static final class Builder extends StaticResourcePack.Builder<StaticDataPack, Builder> {
    /**
     * Initializes a builder for bundled server data.
     *
     * @param identifier owning mod namespace and pack path.
     */
    private Builder(Identifier identifier) {
      super(identifier, PackType.SERVER_DATA);
    }

    /**
     * Returns this data-pack builder.
     *
     * @return this builder.
     */
    @Override
    protected Builder self() {
      return this;
    }

    /**
     * Creates a data-pack definition without loading or registering its resources.
     *
     * @return immutable data-pack definition.
     */
    @Override
    public StaticDataPack build() {
      return new StaticDataPack(this);
    }
  }
}
