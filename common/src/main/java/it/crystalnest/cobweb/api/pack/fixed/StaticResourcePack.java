package it.crystalnest.cobweb.api.pack.fixed;

import it.crystalnest.cobweb.api.pack.PackActivation;
import it.crystalnest.cobweb.platform.Services;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Immutable definition of a pack bundled with the mod identified by its namespace.
 * Build definitions during mod initialization, then explicitly {@link #register()} them.
 * Metadata and resources are read by the native repository when it discovers packs.
 */
public abstract class StaticResourcePack {
  /**
   * Logical identifier of the bundled pack.
   */
  private final Identifier identifier;

  /**
   * Native type of resources provided by the pack.
   */
  private final PackType type;

  /**
   * Display title captured from the builder.
   */
  private final Component title;

  /**
   * Native source, independent of activation.
   */
  private final PackSource source;

  /**
   * Initial optional selection or required activation.
   */
  private final PackActivation activation;

  /**
   * Initial insertion side in the native selection order.
   */
  private final Pack.Position position;

  /**
   * Whether the selection UI anchors the pack at its insertion side.
   */
  private final boolean fixedPosition;

  /**
   * Captures a builder's settings without loading or registering resources.
   *
   * @param builder configured builder.
   * @param type native resource type.
   */
  StaticResourcePack(Builder<?, ?> builder, PackType type) {
    identifier = builder.identifier;
    this.type = type;
    title = builder.title.copy();
    source = builder.source;
    activation = builder.activation;
    position = builder.position;
    fixedPosition = builder.fixedPosition;
  }

  /**
   * Returns the logical pack identifier.
   *
   * @return logical identifier supplied to the builder, without the bundled directory prefix.
   */
  public final Identifier identifier() {
    return identifier;
  }

  /**
   * Returns the native pack type.
   *
   * @return native resource type.
   */
  public final PackType type() {
    return type;
  }

  /**
   * Returns a copy of the configured title.
   *
   * @return a copy of the configured title.
   */
  public final Component title() {
    return title.copy();
  }

  /**
   * Returns the original native source.
   *
   * @return native source, preserved independently from activation.
   */
  public final PackSource source() {
    return source;
  }

  /**
   * Returns the activation mode.
   *
   * @return initial/default or required activation behavior.
   */
  public final PackActivation activation() {
    return activation;
  }

  /**
   * Returns the initial insertion side.
   *
   * @return initial insertion side; saved ordering takes precedence for movable packs.
   */
  public final Pack.Position position() {
    return position;
  }

  /**
   * Returns whether placement is fixed in the native UI.
   *
   * @return whether the native selection UI anchors the pack at its insertion side.
   */
  public final boolean fixedPosition() {
    return fixedPosition;
  }

  /**
   * Returns the fixed bundled directory.
   *
   * @return fixed bundled directory: {@code datapack} or {@code texturepack}.
   */
  public final String directory() {
    return type == PackType.SERVER_DATA ? "datapack" : "texturepack";
  }

  /**
   * Returns the bundled resource path.
   *
   * @return pack root relative to the owning mod's resources.
   */
  public final String resourcePath() {
    return directory() + "/" + identifier.getPath();
  }

  /**
   * Returns the stable native pack ID.
   *
   * @return stable native identity used in saved selections.
   */
  public final String packId() {
    return "mod/" + identifier.getNamespace() + ":" + resourcePath();
  }

  /**
   * Returns the owning mod version.
   *
   * @return owning mod's version.
   */
  public final String version() {
    return Services.PLATFORM.getModVersion(identifier.getNamespace());
  }

  /**
   * Reads native metadata and creates a pack using the current loader's resource supplier.
   *
   * @return native pack, or {@code null} if native metadata loading rejects it (with native diagnostics).
   * @throws IllegalArgumentException if the owning mod is not loaded.
   */
  @Nullable
  public final Pack toPack() {
    return Services.REGISTRY.createStaticResourcePack(this);
  }

  /**
   * Registers this definition during normal mod initialization.
   */
  public final void register() {
    Services.REGISTRY.registerStaticResourcePack(this);
  }

  /**
   * Shared typed builder. Building does not register or read the pack.
   * Defaults are disabled, top placement, movable and built-in source. Default title keys are
   * {@code datapack.<namespace>.<path>} or {@code texturepack.<namespace>.<path>}.
   *
   * @param <P> concrete pack type.
   * @param <B> concrete builder type.
   */
  public abstract static class Builder<P extends StaticResourcePack, B extends Builder<P, B>> {
    /**
     * Owning mod namespace and pack path.
     */
    private final Identifier identifier;

    /**
     * Display title, initially using the generated translation key.
     */
    private Component title;

    /**
     * Native source used to label the pack and retain native source semantics.
     */
    private PackSource source = PackSource.BUILT_IN;

    /**
     * Activation applied when the pack is first encountered or required.
     */
    private PackActivation activation = PackActivation.DEFAULT_DISABLED;

    /**
     * Initial top or bottom placement.
     */
    private Pack.Position position = Pack.Position.TOP;

    /**
     * Whether placement is fixed in the native selection UI.
     */
    private boolean fixedPosition;

    /**
     * Initializes default settings and the type-specific title translation key.
     *
     * @param identifier owning mod namespace and pack path.
     * @param type native resource type.
     */
    Builder(Identifier identifier, PackType type) {
      this.identifier = Objects.requireNonNull(identifier);
      title = Component.translatable((type == PackType.SERVER_DATA ? "datapack" : "texturepack") + "." + identifier.getNamespace() + "." + identifier.getPath());
    }

    /**
     * Returns this concrete builder.
     *
     * @return concrete builder.
     */
    protected abstract B self();

    /**
     * Sets a title component, taking a native component copy.
     *
     * @param title title; use {@link Component#literal(String)} for literal text.
     * @return this builder.
     */
    public final B title(Component title) {
      this.title = Objects.requireNonNull(title).copy();
      return self();
    }

    /**
     * Sets a translatable title; strings are always translation keys.
     *
     * @param translationKey title translation key.
     * @return this builder.
     */
    public final B title(String translationKey) {
      return title(Component.translatable(Objects.requireNonNull(translationKey)));
    }

    /**
     * Sets the native pack source.
     *
     * @param source native source, default {@link PackSource#BUILT_IN}.
     * @return this builder.
     */
    public final B source(PackSource source) {
      this.source = Objects.requireNonNull(source);
      return self();
    }

    /**
     * Sets the activation mode.
     *
     * @param activation activation mode, default {@link PackActivation#DEFAULT_DISABLED}.
     * @return this builder.
     */
    public final B activation(PackActivation activation) {
      this.activation = Objects.requireNonNull(activation);
      return self();
    }

    /**
     * Sets the initial insertion side.
     *
     * @param position initial placement, default {@link Pack.Position#TOP}.
     * @return this builder.
     */
    public final B position(Pack.Position position) {
      this.position = Objects.requireNonNull(position);
      return self();
    }

    /**
     * Sets whether placement is fixed in the native UI.
     *
     * @param fixedPosition whether the native UI fixes placement, default {@code false}.
     * @return this builder.
     */
    public final B fixedPosition(boolean fixedPosition) {
      this.fixedPosition = fixedPosition;
      return self();
    }

    /**
     * Creates an immutable pack definition.
     *
     * @return a new immutable definition; no registration or resource access occurs.
     */
    public abstract P build();
  }
}
