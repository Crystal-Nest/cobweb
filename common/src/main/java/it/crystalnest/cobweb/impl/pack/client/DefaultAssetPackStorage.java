package it.crystalnest.cobweb.impl.pack.client;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import it.crystalnest.cobweb.Constants;
import it.crystalnest.cobweb.api.pack.PackActivation;
import it.crystalnest.cobweb.api.pack.fixed.StaticResourcePack;
import it.crystalnest.cobweb.impl.pack.StaticPackSelection;
import it.crystalnest.cobweb.platform.Services;
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
 * Records discovery only; Minecraft options remain the authority for enabled packs and their order.
 */
@ApiStatus.Internal
public final class DefaultAssetPackStorage {
  /**
   * Codec for the set of previously encountered pack IDs stored in the tracker.
   */
  private static final Codec<Set<String>> CODEC = Codec.STRING.listOf()
    .<Set<String>>xmap(HashSet::new, values -> values.stream().sorted().toList())
    .fieldOf("seen")
    .codec();

  /**
   * Prevents instantiation of the discovery tracker utilities.
   */
  private DefaultAssetPackStorage() {}

  /**
   * Computes first-discovery defaults after native compatibility filtering.
   *
   * @param tracker tracker path in the game directory.
   * @param repository discovered and selected client repository.
   * @return new selection, or null on tracking failure (leave native selection untouched).
   */
  @Nullable
  public static List<String> process(Path tracker, PackRepository repository) {
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
      StaticResourcePack definition = StaticPackSelection.definition(PackType.CLIENT_RESOURCES, pack);
      if (definition == null || Services.PLATFORM.isPackHidden(pack)) {
        continue;
      }
      seen.add(pack.getId());
      if (!previous.contains(pack.getId()) && definition.activation() == PackActivation.DEFAULT_ENABLED && pack.getCompatibility().isCompatible() && !selected.contains(pack)) {
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
