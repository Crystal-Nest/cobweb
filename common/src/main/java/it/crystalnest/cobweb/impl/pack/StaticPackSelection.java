package it.crystalnest.cobweb.impl.pack;

import it.crystalnest.cobweb.api.pack.PackActivation;
import it.crystalnest.cobweb.api.pack.fixed.StaticResourcePack;
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
 * Small selection adaptations for registered static packs; ordinary native packs are unaffected.
 */
@ApiStatus.Internal
public final class StaticPackSelection {
  /**
   * Prevents instantiation of the selection utilities.
   */
  private StaticPackSelection() {}

  /**
   * Finds a registered Cobweb pack definition.
   *
   * @param type pack type.
   * @param pack native pack.
   * @return registered definition, if managed by Cobweb.
   */
  @Nullable
  public static StaticResourcePack definition(PackType type, Pack pack) {
    return Services.REGISTRY.staticResourcePack(type, pack.getId());
  }

  /**
   * Resolves the initial data-pack activation decision.
   *
   * @param pack data pack.
   * @param nativeDefault native source decision.
   * @return initial auto-add decision.
   */
  public static boolean addAutomatically(Pack pack, boolean nativeDefault) {
    StaticResourcePack definition = definition(PackType.SERVER_DATA, pack);
    // Required packs are inserted by the repository, preserving their native position rules.
    return definition == null ? nativeDefault : definition.activation() == PackActivation.DEFAULT_ENABLED;
  }

  /**
   * Checks whether Cobweb requires this pack.
   *
   * @param type pack type.
   * @param pack pack.
   * @return whether this is an always-active Cobweb definition.
   */
  public static boolean isAlwaysActive(PackType type, Pack pack) {
    StaticResourcePack definition = definition(type, pack);
    return definition != null && definition.activation() == PackActivation.ALWAYS_ACTIVE;
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
    List<Pack> added = ordered.stream()
      .filter(pack -> !old.contains(pack.getId()) && definition(PackType.SERVER_DATA, pack) != null)
      .toList();
    ordered.removeAll(added);
    for (Pack pack : added) {
      pack.getDefaultPosition().insert(ordered, pack, Pack::selectionConfig, false);
    }
    return ids(ordered);
  }

  /**
   * Adapts native /reload discovery without changing other packs' discovery behavior.
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
      StaticResourcePack definition = Services.REGISTRY.staticResourcePack(PackType.SERVER_DATA, id);
      Pack pack = repository.getPack(id);
      return definition != null && !previous.contains(id) && definition.activation() != PackActivation.ALWAYS_ACTIVE
             && (definition.activation() == PackActivation.DEFAULT_DISABLED
                 || pack != null && !pack.getRequestedFeatures().isSubsetOf(world.getDataConfiguration().enabledFeatures()));
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
    List<Pack> ordered = packs(repository, selected);
    for (Pack pack : repository.getAvailablePacks()) {
      if (isAlwaysActive(PackType.SERVER_DATA, pack) && !ordered.contains(pack)) {
        pack.getDefaultPosition().insert(ordered, pack, Pack::selectionConfig, false);
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
