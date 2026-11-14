package it.crystalnest.cobweb.api.pack.selection;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import it.crystalnest.cobweb.Constants;
import it.crystalnest.cobweb.api.pack.model.PackActivation;
import it.crystalnest.cobweb.platform.Services;
import net.minecraft.client.Options;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Client-only startup integration and native selection-saving policy.
 */
@ApiStatus.Internal
public final class ClientPackSelection {
  private ClientPackSelection() {}

  /**
   * Checks whether the pack should be saved even with a fixed position.
   *
   * @param pack native pack.
   * @return whether a visible Cobweb pack should be persisted even if fixed.
   */
  public static boolean persistFixed(Pack pack) {
    return Services.PACK.activation(PackType.CLIENT_RESOURCES, pack.getId()) != null && !Services.PLATFORM.isPackHidden(pack);
  }

  /**
   * Applies discovery defaults after native filtering, then saves effective selection without starting a reload.
   *
   * @param options loaded native options.
   * @param repository discovered native repository.
   * @param gameDirectory current game directory.
   */
  public static void initialize(Options options, PackRepository repository, Path gameDirectory) {
    List<String> selection = AssetPackDiscoveryTracker.process(gameDirectory.resolve("data/cobweb_resource_packs.json"), repository);
    if (selection != null) {
      repository.setSelected(selection);
    }
    List<Pack> saved = repository.getSelectedPacks().stream()
      .filter(pack -> !Services.PLATFORM.isPackHidden(pack) && (!pack.isFixedPosition() || persistFixed(pack)))
      .toList();
    List<String> enabled = saved.stream().map(Pack::getId).toList();
    List<String> incompatible = saved.stream().filter(pack -> !pack.getCompatibility().isCompatible()).map(Pack::getId).toList();
    if (!options.resourcePacks.equals(enabled) || !options.incompatibleResourcePacks.equals(incompatible)) {
      options.resourcePacks.clear();
      options.resourcePacks.addAll(enabled);
      options.incompatibleResourcePacks.clear();
      options.incompatibleResourcePacks.addAll(incompatible);
      options.save();
    }
  }

  /**
   * Tracks discovered Cobweb asset packs and computes their first-discovery defaults.
   * Minecraft options store the enabled packs and their order.
   */
  private static final class AssetPackDiscoveryTracker {
    /**
     * Codec for the set of previously encountered pack IDs stored in the tracker.
     */
    private static final Codec<Set<String>> CODEC = Codec.STRING.listOf()
      .<Set<String>>xmap(HashSet::new, values -> values.stream().sorted().toList())
      .fieldOf("seen")
      .codec();

    private AssetPackDiscoveryTracker() {}

    /**
     * Computes first-discovery defaults after native compatibility filtering.
     *
     * @param tracker tracker path in the game directory.
     * @param repository discovered and selected client repository.
     * @return new selection, or {@code null} on tracking failure (leave native selection untouched).
     */
    @Nullable
    private static List<String> process(Path tracker, PackRepository repository) {
      Set<String> previous;
      try {
        previous = CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(tracker, StandardCharsets.UTF_8))).getOrThrow();
      } catch (NoSuchFileException missing) {
        previous = Set.of();
      } catch (IOException | RuntimeException failure) {
        Constants.LOGGER.warn("Cannot read static resource pack tracker {}; retaining saved selections", tracker, failure);
        return null;
      }

      Set<String> seen = new HashSet<>();
      List<Pack> selected = new ArrayList<>(repository.getSelectedPacks());
      for (Pack pack : repository.getAvailablePacks()) {
        PackActivation activation = Services.PACK.activation(PackType.CLIENT_RESOURCES, pack.getId());
        if (activation == null || Services.PLATFORM.isPackHidden(pack)) {
          continue;
        }
        seen.add(pack.getId());
        if (!previous.contains(pack.getId()) && activation == PackActivation.DEFAULT_ENABLED && pack.getCompatibility().isCompatible() && !selected.contains(pack)) {
          pack.getDefaultPosition().insert(selected, pack, Pack::selectionConfig, false);
        }
      }

      if (!seen.equals(previous)) {
        try {
          write(tracker, seen);
        } catch (IOException | RuntimeException failure) {
          Constants.LOGGER.warn("Cannot save static resource pack tracker {}; retaining saved selections", tracker, failure);
          return null;
        }
      }
      return selected.stream().map(Pack::getId).toList();
    }

    /**
     * Replaces the tracker through a temporary file, using an atomic move where supported.
     *
     * @param tracker target tracker path.
     * @param seen currently available pack IDs that have been encountered.
     * @throws IOException if the tracker cannot be written or replaced.
     */
    private static void write(Path tracker, Set<String> seen) throws IOException {
      Files.createDirectories(tracker.getParent());
      Path temporary = Files.createTempFile(tracker.getParent(), "cobweb_resource_packs-", ".tmp");
      try {
        Files.writeString(temporary, CODEC.encodeStart(JsonOps.INSTANCE, seen).getOrThrow().toString(), StandardCharsets.UTF_8);
        try {
          Files.move(temporary, tracker, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
          Files.move(temporary, tracker, StandardCopyOption.REPLACE_EXISTING);
        }
      } finally {
        Files.deleteIfExists(temporary);
      }
    }
  }
}
