package it.crystalnest.cobweb.api.pack.query;

import it.crystalnest.cobweb.api.pack.query.PackQueryLifecycle.Snapshot;
import it.crystalnest.cobweb.api.pack.query.PackQueryLifecycle.Target;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Supplier;

/**
 * Provides live and captured observations of pack selection and native priority.
 * <p>
 * The factory methods create live views. Generation and presentation callbacks receive views captured for the current load or editor state.
 */
public final class PackQueries {
  /**
   * Supplies the observed pack states.
   */
  private final Supplier<Snapshot> observation;

  /**
   * Supplies the live export target, or is {@code null} for a captured view.
   */
  @Nullable
  private final Supplier<Target> target;

  /**
   * @param observation supplier of live or captured pack states.
   * @param target supplier of the live export target, or {@code null} for a captured view.
   */
  PackQueries(Supplier<Snapshot> observation, @Nullable Supplier<Target> target) {
    this.observation = observation;
    this.target = target;
  }

  /**
   * Observes the current client asset repository.
   * Queries made before the repository is bound report unavailable states.
   *
   * @return live view of client asset packs.
   */
  public static PackQueries client() {
    return PackQueryLifecycle.client();
  }

  /**
   * Observes the supplied server's data pack repository.
   *
   * @param server server that owns the data pack repository.
   * @return live view of that server's data packs.
   */
  public static PackQueries forServer(MinecraftServer server) {
    return PackQueryLifecycle.forServer(server);
  }

  /**
   * Checks one pack against a specific observed state.
   *
   * @param reference queried pack.
   * @param question pack state to inspect.
   * @return {@link PackStatus#YES} if any entry matches, {@link PackStatus#NO} if the observed state has no match, or {@link PackStatus#UNAVAILABLE} if the state cannot be observed.
   */
  public PackStatus query(PackReference reference, PackQuestion question) {
    Snapshot snapshot = observation.get();
    List<PackInfo> packs = switch (question) {
      case AVAILABLE -> snapshot.available();
      case SELECTED -> snapshot.selected();
      case REQUESTED -> snapshot.requested();
      case APPLIED -> snapshot.applied();
      case EDITED_SELECTED -> snapshot.edited();
    };
    if (packs == null) {
      return PackStatus.UNAVAILABLE;
    }
    return packs.stream().anyMatch(pack -> pack.matches(reference)) ? PackStatus.YES : PackStatus.NO;
  }

  /**
   * Returns the pack order from lowest to highest native priority.
   *
   * @param order selection to inspect.
   * @return an immutable list inside the optional, including an empty list for an observed empty selection, or an empty optional when the state is unavailable.
   */
  public Optional<List<PackInfo>> order(PackOrder order) {
    Snapshot snapshot = observation.get();
    return Optional.ofNullable(switch (order) {
      case SELECTED -> snapshot.selected();
      case REQUESTED -> snapshot.requested();
      case APPLIED -> snapshot.applied();
      case EDITED -> snapshot.edited();
    });
  }

  /**
   * Returns a pack's index when it identifies one entry in the requested order.
   *
   * @param reference queried pack.
   * @param order selection to inspect.
   * @return zero-based priority index, empty when unavailable, absent or ambiguous.
   */
  public OptionalInt position(PackReference reference, PackOrder order) {
    var observed = order(order);
    if (observed.isEmpty()) {
      return OptionalInt.empty();
    }
    var packs = observed.get();
    int found = -1;
    for (int index = 0; index < packs.size(); index++) {
      if (packs.get(index).matches(reference)) {
        if (found >= 0) {
          return OptionalInt.empty();
        }
        found = index;
      }
    }
    return found < 0 ? OptionalInt.empty() : OptionalInt.of(found);
  }

  /**
   * Resolves the current export target for a live view.
   *
   * @return the live target, or {@code null} if the view is captured or the client has yet to bind its repository.
   */
  @Nullable
  Target target() {
    return target == null ? null : target.get();
  }

  @Override
  public String toString() {
    return observation.get().toString();
  }
}
