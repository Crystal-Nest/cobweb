package it.crystalnest.cobweb.impl.pack.client;

import com.mojang.blaze3d.platform.NativeImage;
import it.crystalnest.cobweb.Constants;
import it.crystalnest.cobweb.api.pack.PackIcon;
import it.crystalnest.cobweb.api.pack.PackInfo;
import it.crystalnest.cobweb.api.pack.PackPresentation;
import it.crystalnest.cobweb.api.pack.PackPresentationContext;
import it.crystalnest.cobweb.api.pack.PackQueries;
import it.crystalnest.cobweb.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
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
   * @return presentation fields or null when the pack has no custom presentation.
   */
  @Nullable
  public Display display(Pack pack, PackRepository repository, List<Pack> selected) {
    return values.computeIfAbsent(pack.getId(), id -> {
      var minecraft = Minecraft.getInstance();
      var type = minecraft.getResourcePackRepository() == repository ? PackType.CLIENT_RESOURCES : PackType.SERVER_DATA;
      var definition = Services.REGISTRY.packDeclaration(type, id);
      if (definition == null || definition.icon() == null && definition.presentation() == null) {
        return Optional.empty();
      }
      if (queries == null) {
        queries = PackQueries.editor(type, repository, selected);
      }
      PackPresentation patch = null;
      if (definition.presentation() != null) {
        try {
          patch = definition.presentation().apply(new PackPresentationContext(new PackInfo(id, definition.id()), type, queries));
        } catch (RuntimeException failure) {
          Constants.LOGGER.warn("Unable to refresh pack presentation for {}", id, failure);
        }
      }
      PackIcon icon = patch == null ? null : patch.icon();
      Identifier texture = icon == null ? null : texture(pack, icon);
      if (texture == null && definition.icon() != null && !definition.icon().equals(icon)) {
        texture = texture(pack, definition.icon());
      }
      return Optional.of(new Display(patch, texture));
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
