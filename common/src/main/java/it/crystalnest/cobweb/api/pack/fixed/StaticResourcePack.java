package it.crystalnest.cobweb.api.pack.fixed;

import it.crystalnest.cobweb.platform.Services;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Resource pack built-in within a mod.
 */
public abstract class StaticResourcePack {
  /**
   * Pack identifier.
   */
  private final Identifier identifier;

  /**
   * Pack type.
   */
  private final PackType type;

  /**
   * Pack display name.
   */
  private final Component name;

  /**
   * Pack source.
   */
  private final PackSource source;

  /**
   * Pack position.
   */
  private final Pack.Position position;

  /**
   * Whether the pack is always active (can't be deactivated).
   */
  private final boolean alwaysActive;

  /**
   * @param identifier {@link #identifier}.
   * @param type {@link #type}.
   * @param source {@link #source}.
   * @param position {@link #position}.
   * @param alwaysActive {@link #alwaysActive}.
   */
  protected StaticResourcePack(Identifier identifier, PackType type, PackSource source, Pack.Position position, boolean alwaysActive) {
    this.identifier = Identifier.fromNamespaceAndPath(identifier.getNamespace(), directory() + "/" + identifier.getPath());
    this.type = type;
    this.name = Component.translatable(directory() + "." + identifier.getNamespace() + "." + identifier.getPath());
    this.source = source;
    this.position = position;
    this.alwaysActive = alwaysActive;
  }

  /**
   * @param identifier {@link #identifier}.
   * @param type {@link #type}.
   * @param position {@link #position}.
   */
  protected StaticResourcePack(Identifier identifier, PackType type, Pack.Position position) {
    this(identifier, type, PackSource.BUILT_IN, position, false);
  }

  /**
   * Creates a {@link Pack} from this pack.
   *
   * @return this pack as a {@link Pack}.
   */
  public Pack toPack() {
    if (Services.PLATFORM.isModLoaded(identifier.getNamespace())) {
      return Pack.readMetaAndCreate(
        new PackLocationInfo("mod/" + identifier, name, source, Optional.of(new KnownPack("cobweb", "mod/" + identifier, version()))),
        new StaticResourcesSupplier(Services.PLATFORM.getResourcePath(identifier.getNamespace(), identifier.getPath())),
        type,
        new PackSelectionConfig(alwaysActive, position, false)
      );
    }
    throw new IllegalArgumentException("Mod not found: " + identifier.getNamespace());
  }

  /**
   * Returns this {@link #identifier}.
   *
   * @return {@link #identifier}.
   */
  public Identifier identifier() {
    return identifier;
  }

  /**
   * Returns this {@link #type}.
   *
   * @return {@link #type}.
   */
  public PackType type() {
    return type;
  }

  /**
   * Returns this {@link #name}.
   *
   * @return {@link #name}.
   */
  public Component name() {
    return name;
  }

  /**
   * Returns this {@link #source}.
   *
   * @return {@link #source}.
   */
  public PackSource source() {
    return source;
  }

  /**
   * Returns this {@link #position}.
   *
   * @return {@link #position}.
   */
  public Pack.Position position() {
    return position;
  }

  /**
   * Returns this {@link #alwaysActive}.
   *
   * @return {@link #alwaysActive}.
   */
  public boolean alwaysActive() {
    return alwaysActive;
  }

  /**
   * Pack version, equal to the owning mod's version.
   *
   * @return pack version.
   */
  public String version() {
    return Services.PLATFORM.getModVersion(identifier.getNamespace());
  }

  /**
   * Pack directory under {@code resources}.
   *
   * @return pack directory.
   */
  public abstract String directory();

  /**
   * Registers this resource pack to the game.
   */
  public void register() {
    Services.REGISTRY.registerStaticResourcePack(this);
  }

  /**
   * Static resources supplier.
   */
  public static class StaticResourcesSupplier implements Pack.ResourcesSupplier {
    /**
     * Resource path.
     */
    private final Path path;

    /**
     * @param path {@link #path}.
     */
    public StaticResourcesSupplier(Path path) {
      this.path = path;
    }

    @NotNull
    @Override
    public PackResources openPrimary(@NotNull PackLocationInfo info) {
      return openInfo(info);
    }

    @NotNull
    @Override
    public PackResources openFull(@NotNull PackLocationInfo info, @NotNull Pack.Metadata metadata) {
      return openInfo(info);
    }

    /**
     * Opens the pack resources from the given {@link PackLocationInfo}.
     *
     * @param info {@link PackLocationInfo}.
     * @return {@link PackResources}.
     */
    private PackResources openInfo(@NotNull PackLocationInfo info) {
      return new PathPackResources(info, path);
    }
  }
}
