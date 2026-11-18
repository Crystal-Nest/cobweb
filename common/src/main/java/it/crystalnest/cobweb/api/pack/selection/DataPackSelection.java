package it.crystalnest.cobweb.api.pack.selection;

import it.crystalnest.cobweb.Constants;
import it.crystalnest.cobweb.api.pack.generation.PackLoadLog;
import it.crystalnest.cobweb.api.pack.query.PackActivation;
import it.crystalnest.cobweb.platform.Services;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.storage.WorldData;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Applies Cobweb activation and initial placement policies to native data pack selection.
 */
@ApiStatus.Internal
public final class DataPackSelection {
  private DataPackSelection() {}

  /**
   * Finds a registered data pack's activation policy.
   *
   * @param pack native pack.
   * @return registered activation policy, or {@code null} for an unmanaged pack.
   */
  @Nullable
  private static PackActivation activation(Pack pack) {
    return Services.PACK.activation(PackType.SERVER_DATA, pack.getId());
  }

  /**
   * Resolves the initial data-pack activation decision.
   *
   * @param pack data pack.
   * @param nativeDefault native source decision.
   * @return initial auto-add decision.
   */
  public static boolean addAutomatically(Pack pack, boolean nativeDefault) {
    PackActivation activation = activation(pack);
    // Required packs are inserted by the repository, preserving their native position rules.
    return activation == null ? nativeDefault : activation == PackActivation.DEFAULT_ENABLED;
  }

  /**
   * Checks whether Cobweb requires this data pack.
   *
   * @param pack native data pack.
   * @return whether this is an always-active Cobweb data pack.
   */
  public static boolean isAlwaysActive(Pack pack) {
    return activation(pack) == PackActivation.ALWAYS_ACTIVE;
  }

  /**
   * Orders only newly selected Cobweb packs using native insertion, leaving saved order intact.
   *
   * @param repository repository.
   * @param selected new selection.
   * @param previous previously selected IDs.
   * @return ordered selection.
   */
  public static List<String> orderNewPacks(PackRepository repository, Collection<String> selected, Collection<String> previous) {
    Set<String> old = new HashSet<>(previous);
    List<Pack> ordered = packs(repository, selected);
    List<Pack> added = ordered.stream().filter(pack -> !old.contains(pack.getId()) && activation(pack) != null).toList();
    ordered.removeAll(added);
    for (Pack pack : added) {
      pack.getDefaultPosition().insert(ordered, pack, Pack::selectionConfig, false);
    }
    return ids(ordered);
  }

  /**
   * Adapts native {@code /reload} discovery without changing other packs' discovery behavior.
   *
   * @param repository reloaded repository.
   * @param world world configuration.
   * @param previous pre-reload selection.
   * @param discovered native discovered selection.
   * @return selection respecting Cobweb activation and initial placement.
   */
  public static List<String> discover(PackRepository repository, WorldData world, Collection<String> previous, Collection<String> discovered) {
    List<String> selected = new ArrayList<>(discovered);
    selected.removeIf(id -> {
      PackActivation activation = Services.PACK.activation(PackType.SERVER_DATA, id);
      if (activation == null || previous.contains(id) || activation == PackActivation.ALWAYS_ACTIVE) {
        return false;
      }
      if (activation == PackActivation.DEFAULT_DISABLED) {
        Constants.LOGGER.debug("Data pack discovery left {} disabled by default", id);
        return true;
      }
      Pack pack = repository.getPack(id);
      boolean missingFeatures = pack != null && !pack.getRequestedFeatures().isSubsetOf(world.getDataConfiguration().enabledFeatures());
      if (missingFeatures) {
        Constants.LOGGER.debug("Data pack discovery left {} disabled because the world lacks its requested features", id);
      }
      return missingFeatures;
    });
    return requirePacks(repository, orderNewPacks(repository, selected, previous));
  }

  /**
   * Restores required Cobweb packs before resource opening, without mutating repository state on a failed reload.
   *
   * @param repository repository.
   * @param selected requested selection.
   * @return selection including always-active packs.
   */
  public static List<String> requirePacks(PackRepository repository, Collection<String> selected) {
    return requirePacks(repository, selected, null);
  }

  /**
   * Restores required packs and records the decision in a native load trace when supplied.
   *
   * @param repository repository.
   * @param selected requested pack IDs.
   * @param log native load trace, or {@code null} before a reload is requested.
   * @return selection including always-active packs.
   */
  public static List<String> requirePacks(PackRepository repository, Collection<String> selected, @Nullable PackLoadLog log) {
    List<Pack> ordered = packs(repository, selected);
    for (Pack pack : repository.getAvailablePacks()) {
      if (isAlwaysActive(pack) && !ordered.contains(pack)) {
        pack.getDefaultPosition().insert(ordered, pack, Pack::selectionConfig, false);
        if (log == null) {
          Constants.LOGGER.debug("Data pack discovery restored always-active pack {}", pack.getId());
        } else {
          log.restoredRequired(pack.getId());
        }
      }
    }
    return ids(ordered);
  }

  /**
   * Resolves selected IDs to available native packs, preserving their order.
   *
   * @param repository native repository.
   * @param ids selected pack IDs.
   * @return mutable list of available packs.
   */
  private static List<Pack> packs(PackRepository repository, Collection<String> ids) {
    return new ArrayList<>(ids.stream().map(repository::getPack).filter(Objects::nonNull).toList());
  }

  /**
   * Extracts native IDs in the supplied selection order.
   *
   * @param packs ordered native packs.
   * @return ordered pack IDs.
   */
  private static List<String> ids(List<Pack> packs) {
    return packs.stream().map(Pack::getId).toList();
  }
}
