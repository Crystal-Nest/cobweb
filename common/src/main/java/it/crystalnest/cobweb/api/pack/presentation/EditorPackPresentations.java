package it.crystalnest.cobweb.api.pack.presentation;

import com.mojang.blaze3d.platform.NativeImage;
import it.crystalnest.cobweb.Constants;
import it.crystalnest.cobweb.api.pack.query.PackInfo;
import it.crystalnest.cobweb.api.pack.query.PackQueries;
import it.crystalnest.cobweb.api.pack.query.PackQueryLifecycle;
import it.crystalnest.cobweb.platform.Services;
import it.crystalnest.cobweb.platform.services.PackHelper.PackDeclaration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves and caches presentation for one Minecraft pack selection model.
 * Releases the cached textures when the editor state is invalidated.
 */
@ApiStatus.Internal
public final class EditorPackPresentations {
  /**
   * Evaluated display patches by native pack ID, including packs with no overrides.
   */
  private final Map<String, Optional<Display>> values = new HashMap<>();

  /**
   * Uploaded icons and failed icon lookups for the current editor state.
   */
  private final Map<IconKey, Optional<Identifier>> textures = new HashMap<>();

  /**
   * Editor observations shared by current display patches, or {@code null} until the first lookup.
   */
  @Nullable
  private PackQueries queries = null;

  /**
   * Resolves presentation once per pack for the current editor state.
   * Uses explicit Java base settings and callback fields where supplied. Falls back to file or native presentation for the other fields.
   *
   * @param pack native pack.
   * @param repository editor repository.
   * @param selected proposed selection in native resource priority order.
   * @return resolved text and texture overrides, or {@code null} when the native presentation applies.
   */
  @Nullable
  public Display display(Pack pack, PackRepository repository, List<Pack> selected) {
    return values.computeIfAbsent(pack.getId(), _ -> resolve(pack, repository, selected)).orElse(null);
  }

  /**
   * Combines base fields, conditional presentation and uploaded icons for one editor entry.
   *
   * @param pack displayed native pack.
   * @param repository editor repository.
   * @param selected proposed pack selection.
   * @return resolved overrides, or an empty result when native presentation applies.
   */
  private Optional<Display> resolve(Pack pack, PackRepository repository, List<Pack> selected) {
    var minecraft = Minecraft.getInstance();
    var type = minecraft.getResourcePackRepository() == repository ? PackType.CLIENT_RESOURCES : PackType.SERVER_DATA;
    if (queries == null) {
      queries = PackQueryLifecycle.editor(type, repository, selected);
    }
    var definition = Services.PACK.packDeclaration(type, pack.getId());
    var file = FilePackPresentation.read(pack, queries, definition == null || definition.presentation() == null);
    var patch = applyPresentation(pack, type, definition, basePresentation(definition, file));
    PackIcon baseIcon = definition == null ? null : definition.icon();
    Identifier texture = patch.icon() == null ? null : texture(pack, patch.icon());
    if (texture == null && baseIcon != null && !baseIcon.equals(patch.icon())) {
      texture = texture(pack, baseIcon);
    }
    if (!(patch.title() == null && patch.description() == null && texture == null)) {
      return Optional.of(new Display(patch, texture));
    }
    return Optional.empty();
  }

  /**
   * Selects file fields only where Java has not explicitly supplied a base field.
   *
   * @param definition registered Java declaration, or {@code null} for an ordinary native pack.
   * @param file file presentation, or {@code null} when absent.
   * @return effective base presentation.
   */
  private static PackPresentation basePresentation(@Nullable PackDeclaration definition, @Nullable PackPresentation file) {
    var title = file == null || definition != null && definition.explicitTitle() ? null : file.title();
    var description = file == null || definition != null && definition.explicitDescription() ? null : file.description();
    PackIcon baseIcon = definition == null ? null : definition.icon();
    PackIcon icon = baseIcon != null || file == null ? baseIcon : file.icon();
    return new PackPresentation(title, description, icon);
  }

  /**
   * Applies the Java presentation callback while retaining base fields omitted by its result.
   * Logs callback failures and preserves the base presentation.
   *
   * @param pack displayed native pack.
   * @param type native resource side.
   * @param definition Java declaration, or {@code null} when absent.
   * @param base effective base presentation.
   * @return callback overrides composed over the base, or the unchanged base.
   */
  private PackPresentation applyPresentation(Pack pack, PackType type, @Nullable PackDeclaration definition, PackPresentation base) {
    if (definition != null && definition.presentation() != null) {
      try {
        var patch = definition.presentation().apply(new PackPresentationContext(new PackInfo(pack.getId(), definition.id()), type, queries));
        if (patch != null) {
          var title = patch.title() == null ? base.title() : patch.title();
          var description = patch.description() == null ? base.description() : patch.description();
          var icon = patch.icon() == null ? base.icon() : patch.icon();
          return new PackPresentation(title, description, icon);
        }
      } catch (RuntimeException failure) {
        Constants.LOGGER.error("Unable to refresh pack presentation for {}", pack.getId(), failure);
      }
    }
    return base;
  }

  /**
   * Releases uploaded presentation textures and clears cached overrides and editor observations.
   * The next display lookup captures the current editor state.
   */
  public void clear() {
    var manager = Minecraft.getInstance().getTextureManager();
    textures.values().forEach(value -> value.ifPresent(manager::release));
    textures.clear();
    values.clear();
    queries = null;
  }

  /**
   * Returns a cached texture or attempts to load and upload the icon.
   *
   * @param pack pack whose editor icon is being resolved.
   * @param icon requested icon source.
   * @return uploaded texture ID, or {@code null} if it could not be loaded.
   */
  @Nullable
  private Identifier texture(Pack pack, PackIcon icon) {
    var key = new IconKey(icon, icon.origin() == PackIcon.Origin.PACK ? pack.getId() : null);
    return textures.computeIfAbsent(key, ignored -> load(pack, icon)).orElse(null);
  }

  /**
   * Opens the icon from its declared source and registers it as a dynamic client texture.
   * Closes the decoded pixels if registration fails.
   *
   * @param pack displayed pack, used for bundled icon lookup and diagnostics.
   * @param icon icon source and path.
   * @return uploaded texture ID, or an empty result after a logged read or upload failure.
   */
  private static Optional<Identifier> load(Pack pack, PackIcon icon) {
    try {
      var resource = icon.resource();
      NativeImage pixels = switch (icon.origin()) {
        case MOD -> {
          var source = Services.PLATFORM.modResource(resource.getNamespace(), "assets/" + resource.getNamespace() + "/" + resource.getPath());
          if (source == null) {
            throw new IOException("Missing mod resource " + resource);
          }
          try (var stream = source.stream().get()) {
            yield NativeImage.read(stream);
          }
        }
        case PACK -> {
          try (var metadata = pack.open()) {
            var source = metadata.getRootResource(resource.getPath().split("/"));
            if (source == null) {
              throw new IOException("Missing bundled pack resource " + resource);
            }
            try (var stream = source.get()) {
              yield NativeImage.read(stream);
            }
          }
        }
        case APPLIED -> {
          try (var stream = Minecraft.getInstance().getResourceManager().open(resource)) {
            yield NativeImage.read(stream);
          }
        }
      };
      return Optional.of(uploadIcon(pixels));
    } catch (IOException | RuntimeException failure) {
      Constants.LOGGER.error("Unable to load presentation icon {} for {}", icon, pack.getId(), failure);
      return Optional.empty();
    }
  }

  /**
   * Registers decoded pixels as a client texture and transfers their ownership to the texture manager.
   * Closes the pixels if registration fails.
   *
   * @param pixels decoded icon pixels owned by the caller.
   * @return registered texture identifier.
   */
  private static Identifier uploadIcon(NativeImage pixels) {
    var id = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "pack_editor/" + UUID.randomUUID());
    try {
      Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, pixels));
      return id;
    } catch (RuntimeException | Error failure) {
      pixels.close();
      throw failure;
    }
  }

  /**
   * Contains resolved editor overrides for one pack.
   *
   * @param patch resolved text override, or {@code null} to use native text.
   * @param texture uploaded icon texture ID, or {@code null} to use the native icon.
   */
  public record Display(@Nullable PackPresentation patch, @Nullable Identifier texture) {}

  /**
   * Identifies a cached editor icon.
   *
   * @param icon icon source and path.
   * @param pack native pack ID for a bundled icon, or {@code null} for an icon shared by source and path.
   */
  private record IconKey(PackIcon icon, @Nullable String pack) {}

  /**
   * Exposes presentation state maintained by the native editor model.
   */
  public interface Model {
    /**
     * Invalidates the current presentation revision.
     */
    void invalidatePackPresentation();

    /**
     * Finds the visible overrides for a pack.
     *
     * @param pack native pack.
     * @return overrides, if any.
     */
    @Nullable
    Display resolvePackPresentation(Pack pack);
  }

  /**
   * Allows an active pack screen to refresh its display without reloading resources.
   */
  public interface Screen {
    /**
     * Refreshes the displayed editor entries.
     */
    void refreshPackPresentation();
  }
}
