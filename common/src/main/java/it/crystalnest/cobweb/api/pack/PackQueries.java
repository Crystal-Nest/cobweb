package it.crystalnest.cobweb.api.pack;

import it.crystalnest.cobweb.impl.pack.NativePackDependencies;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/**
 * Provides live or load-scoped observations of a native pack repository.
 */
public final class PackQueries {
  private static final Map<PackRepository, State> STATES = Collections.synchronizedMap(new WeakHashMap<>());

  @Nullable
  private static volatile Target client;

  private final Supplier<Snapshot> observation;

  @Nullable
  private final Supplier<Target> target;

  private PackQueries(Supplier<Snapshot> observation, @Nullable Supplier<Target> target) {
    this.observation = observation;
    this.target = target;
  }

  /**
   * Observes the current client asset repository where available.
   *
   * @return live asset queries.
   */
  public static PackQueries client() {
    return new PackQueries(() -> {
      Target current = client;
      return current == null ? Snapshot.EMPTY : snapshot(current.type, current.repository);
    }, () -> client);
  }

  /**
   * Observes a server's local data pack repository.
   *
   * @param server running native server.
   * @return live data pack queries.
   */
  public static PackQueries forServer(MinecraftServer server) {
    var target = new Target(PackType.SERVER_DATA, server.getPackRepository(), server::getResourceManager);
    return new PackQueries(() -> snapshot(target.type, target.repository), () -> target);
  }

  /**
   * Checks one pack against a specific observed state.
   *
   * @param reference queried pack.
   * @param question pack state to inspect.
   * @return yes, no or unavailable.
   */
  public PackStatus query(PackReference reference, PackQuestion question) {
    Snapshot snapshot = observation.get();
    List<PackInfo> packs = switch (question) {
      case AVAILABLE -> snapshot.available;
      case SELECTED -> snapshot.selected;
      case REQUESTED -> snapshot.requested;
      case APPLIED -> snapshot.applied;
      case EDITED_SELECTED -> snapshot.edited;
    };
    if (packs == null) {
      return PackStatus.UNAVAILABLE;
    }
    return packs.stream().anyMatch(pack -> pack.matches(reference)) ? PackStatus.YES : PackStatus.NO;
  }

  /**
   * Returns a pack order in increasing native priority.
   *
   * @param order selection to inspect.
   * @return observed order, empty when that state is unavailable.
   */
  public Optional<List<PackInfo>> order(PackOrder order) {
    Snapshot snapshot = observation.get();
    return Optional.ofNullable(switch (order) {
      case SELECTED -> snapshot.selected;
      case REQUESTED -> snapshot.requested;
      case APPLIED -> snapshot.applied;
      case EDITED -> snapshot.edited;
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
   * Binds the client repository and applied resource manager.
   *
   * @param repository client asset repository.
   * @param resources live client asset manager.
   */
  @ApiStatus.Internal
  public static void bindClient(PackRepository repository, ResourceManager resources) {
    client = new Target(PackType.CLIENT_RESOURCES, repository, () -> resources);
  }

  /**
   * Captures the current editor order with repository and applied observations.
   *
   * @param type edited pack type.
   * @param repository native repository.
   * @param edited current selection in the editor's high-to-low priority order.
   * @return immutable editor observations.
   */
  @ApiStatus.Internal
  public static PackQueries editor(PackType type, PackRepository repository, List<Pack> edited) {
    var state = snapshot(type, repository);
    var observed = new Snapshot(state.available, state.selected, state.requested, state.applied, edited.reversed().stream().map(pack -> info(type, pack)).toList());
    return new PackQueries(() -> observed, null);
  }

  /**
   * Captures the repository at the beginning of one native load.
   *
   * @param type native pack type.
   * @param repository repository supplying the selected stack.
   * @param packs requested resource openings in native priority order.
   * @return load-scoped query state and completion handle.
   */
  @ApiStatus.Internal
  public static Request begin(PackType type, PackRepository repository, List<PackResources> packs) {
    List<PackInfo> requested = packs.stream().map(pack -> NativePackDependencies.info(type, pack)).distinct().toList();
    synchronized (STATES) {
      var before = STATES.get(repository);
      var state = new State(requested, before == null ? null : before.applied);
      STATES.put(repository, state);
      Snapshot observed = snapshot(type, repository);
      return new Request(repository, state, new PackQueries(() -> observed, null));
    }
  }

  private static Snapshot snapshot(PackType type, PackRepository repository) {
    State state = STATES.get(repository);
    return new Snapshot(repository.getAvailablePacks().stream().map(pack -> info(type, pack)).toList(), repository.getSelectedPacks().stream().map(pack -> info(type, pack)).toList(),
      state == null ? null : state.requested, state == null ? null : state.applied, null);
  }

  private static PackInfo info(PackType type, Pack pack) {
    try (var metadata = pack.openMetadata()) {
      return NativePackDependencies.info(type, pack.getId(), metadata);
    }
  }

  @Nullable
  Target target() {
    return target == null ? null : target.get();
  }

  @Override
  public String toString() {
    return observation.get().toString();
  }

  /**
   * Carries the captured selection through a native pack load.
   */
  @ApiStatus.Internal
  public static final class Request {
    private final PackRepository repository;

    private final State state;

    private final PackQueries queries;

    private Request(PackRepository repository, State state, PackQueries queries) {
      this.repository = repository;
      this.state = state;
      this.queries = queries;
    }

    /**
     * Returns the observations captured at the start of the load.
     *
     * @return load-scoped observations.
     */
    public PackQueries queries() {
      return queries;
    }

    /**
     * Clears the request and records its order as applied after native success.
     *
     * @param success whether native loading succeeded.
     */
    public void finish(boolean success) {
      synchronized (STATES) {
        if (STATES.get(repository) == state) {
          STATES.put(repository, new State(null, success ? state.requested : state.applied));
        }
      }
    }
  }

  record Target(PackType type, PackRepository repository, Supplier<ResourceManager> resources) {}

  private record State(@Nullable List<PackInfo> requested, @Nullable List<PackInfo> applied) {}

  private record Snapshot(@Nullable List<PackInfo> available, @Nullable List<PackInfo> selected, @Nullable List<PackInfo> requested, @Nullable List<PackInfo> applied, @Nullable List<PackInfo> edited) {
    private static final Snapshot EMPTY = new Snapshot(null, null, null, null, null);
  }
}
