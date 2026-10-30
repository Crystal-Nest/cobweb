package it.crystalnest.cobweb.impl.pack.client;

import it.crystalnest.cobweb.impl.pack.StaticPackSelection;
import it.crystalnest.cobweb.platform.Services;
import net.minecraft.client.Options;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import org.jetbrains.annotations.ApiStatus;

import java.nio.file.Path;
import java.util.List;

/**
 * Client-only startup integration and native selection-saving policy.
 */
@ApiStatus.Internal
public final class ClientPackSelection {
  /**
   * Prevents instantiation of the client selection utilities.
   */
  private ClientPackSelection() {}

  /**
   * Checks whether the pack should be saved even with a fixed position.
   *
   * @param pack native pack.
   * @return whether a visible Cobweb pack should be persisted even if fixed.
   */
  public static boolean persistFixed(Pack pack) {
    return StaticPackSelection.definition(PackType.CLIENT_RESOURCES, pack) != null && !Services.PLATFORM.isPackHidden(pack);
  }

  /**
   * Applies discovery defaults after native filtering, then saves effective selection without starting a reload.
   *
   * @param options loaded native options.
   * @param repository discovered native repository.
   * @param gameDirectory current game directory.
   */
  public static void initialize(Options options, PackRepository repository, Path gameDirectory) {
    List<String> selection = DefaultAssetPackStorage.process(gameDirectory.resolve("data/cobweb_resource_packs.json"), repository);
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
}
