package it.crystalnest.cobweb.api.pack.client;

import com.mojang.blaze3d.platform.NativeImage;
import it.crystalnest.cobweb.Constants;
import it.crystalnest.cobweb.api.pack.presentation.PackIcon;
import it.crystalnest.cobweb.api.pack.presentation.PackPresentation;
import it.crystalnest.cobweb.api.pack.presentation.PackPresentationContext;
import it.crystalnest.cobweb.api.pack.query.PackInfo;
import it.crystalnest.cobweb.api.pack.query.PackQueries;
import it.crystalnest.cobweb.api.pack.query.PackQueryLifecycle;
import it.crystalnest.cobweb.platform.Services;
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
 * Keeps one presentation patch and texture per displayed pack for an editor revision.
 */
@ApiStatus.Internal
public final class EditorPackPresentations {
  private final Map<String, Optional<Display>> values = new HashMap<>();

  private final Map<IconKey, Optional<Identifier>> textures = new HashMap<>();

  @Nullable
  private PackQueries queries;

  /**
   * Returns the current display patch, evaluating it once for the current editor state.
   *
   * @param pack native pack.
   * @param repository editor repository.
   * @param selected selected packs in editor order.
   * @return presentation fields or {@code null} when the pack has no custom presentation.
   */
  @Nullable
  public Display display(Pack pack, PackRepository repository, List<Pack> selected) {
    return values.computeIfAbsent(pack.getId(), id -> {
      var minecraft = Minecraft.getInstance();
      var type = minecraft.getResourcePackRepository() == repository ? PackType.CLIENT_RESOURCES : PackType.SERVER_DATA;
      var definition = Services.PACK.packDeclaration(type, id);
      if (queries == null) {
        queries = PackQueryLifecycle.editor(type, repository, selected);
      }
      var callback = definition == null ? null : definition.presentation();
      var file = FilePackPresentation.read(pack, queries, callback == null);
      var title = file == null || definition != null && definition.explicitTitle() ? null : file.title();
      var description = file == null || definition != null && definition.explicitDescription() ? null : file.description();
      PackIcon baseIcon = definition == null ? null : definition.icon();
      PackIcon icon = baseIcon != null || file == null ? baseIcon : file.icon();
      if (callback != null) {
        try {
          var patch = callback.apply(new PackPresentationContext(new PackInfo(id, definition.id()), type, queries));
          if (patch != null) {
            title = patch.title() == null ? title : patch.title();
            description = patch.description() == null ? description : patch.description();
            icon = patch.icon() == null ? icon : patch.icon();
          }
        } catch (RuntimeException failure) {
          Constants.LOGGER.warn("Unable to refresh pack presentation for {}", id, failure);
        }
      }
      Identifier texture = icon == null ? null : texture(pack, icon);
      if (texture == null && baseIcon != null && !baseIcon.equals(icon)) {
        texture = texture(pack, baseIcon);
      }
      if (title == null && description == null && texture == null) {
        return Optional.empty();
      }
      var patch = PackPresentation.builder();
      if (title != null) {
        patch.title(title);
      }
      if (description != null) {
        patch.description(description);
      }
      return Optional.of(new Display(patch.build(), texture));
    }).orElse(null);
  }

  /**
   * Releases overrides and invalidates observations after discovery or an editor change.
   */
  public void clear() {
    var manager = Minecraft.getInstance().getTextureManager();
    textures.values().forEach(value -> value.ifPresent(manager::release));
    textures.clear();
    values.clear();
    queries = null;
  }

  @Nullable
  private Identifier texture(Pack pack, PackIcon icon) {
    var key = new IconKey(icon, icon.origin() == PackIcon.Origin.PACK ? pack.getId() : null);
    return textures.computeIfAbsent(key, ignored -> load(pack, icon)).orElse(null);
  }

  private Optional<Identifier> load(Pack pack, PackIcon icon) {
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
          try (var metadata = pack.openMetadata()) {
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
      var id = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "pack_editor/" + UUID.randomUUID());
      try {
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, pixels));
        return Optional.of(id);
      } catch (RuntimeException | Error failure) {
        pixels.close();
        throw failure;
      }
    } catch (IOException | RuntimeException failure) {
      Constants.LOGGER.warn("Unable to load presentation icon {} for {}", icon, pack.getId(), failure);
      return Optional.empty();
    }
  }

  /**
   * Contains resolved editor overrides for one pack.
   *
   * @param patch optional component and icon overrides.
   * @param texture optional uploaded icon texture.
   */
  public record Display(@Nullable PackPresentation patch, @Nullable Identifier texture) {}

  private record IconKey(PackIcon icon, @Nullable String pack) {}

  /**
   * Exposes presentation state maintained by the native editor model.
   */
  public interface Model {
    /**
     * Invalidates the current presentation revision.
     */
    void cobweb$refreshPresentation();

    /**
     * Finds the visible overrides for a pack.
     *
     * @param pack native pack.
     * @return overrides, if any.
     */
    @Nullable
    Display cobweb$presentation(Pack pack);
  }

  /**
   * Allows an active pack screen to refresh its display without reloading resources.
   */
  public interface Screen {
    /**
     * Refreshes the displayed editor entries.
     */
    void cobweb$refreshPackPresentation();
  }
}
