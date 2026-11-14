package it.crystalnest.cobweb.api.pack.query;

import com.google.common.collect.MapMaker;
import it.crystalnest.cobweb.api.pack.dependency.NativePackDependencies;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/**
 * Tracks native repository state and supplies live and captured views for pack queries.
 */
@ApiStatus.Internal
public final class PackQueryLifecycle {
  /**
   * Tracks the current request and last successful load for each repository.
   */
  private static final Map<PackRepository, State> STATES = Collections.synchronizedMap(new WeakHashMap<>());

  /**
   * Caches discovered identities by native pack object and resource type.
   * Guava weak keys compare object identity because native pack equality compares locations.
   */
  private static final Map<Pack, Map<PackType, PackInfo>> IDENTITIES = new MapMaker().weakKeys().makeMap();

  /**
   * Live client asset target, or {@code null} before binding.
   */
  @Nullable
  private static volatile Target client;

  private PackQueryLifecycle() {}

  /**
   * Observes the current client asset repository where available.
   *
   * @return live asset queries.
   */
  static PackQueries client() {
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
  static PackQueries forServer(MinecraftServer server) {
    var target = new Target(PackType.SERVER_DATA, server.getPackRepository(), server::getResourceManager);
    return new PackQueries(() -> snapshot(target.type, target.repository), () -> target);
  }

  /**
   * Binds the client repository and applied resource manager.
   *
   * @param repository client asset repository.
   * @param resources live client asset manager.
   */
  public static void bindClient(PackRepository repository, ResourceManager resources) {
    client = new Target(PackType.CLIENT_RESOURCES, repository, () -> resources);
  }

  /**
   * Captures the proposed editor order alongside repository and load state.
   *
   * @param type edited pack type.
   * @param repository native repository.
   * @param edited current selection in the editor's high-to-low priority order.
   * @return immutable editor observations.
   */
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
   * @param packs requested resource openings in increasing native priority.
   * @return load-scoped query state and completion handle.
   */
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

  /**
   * Observes repository availability, selection, and load state.
   *
   * @param type native pack type.
   * @param repository repository to observe.
   * @return detached observations of the repository.
   */
  private static Snapshot snapshot(PackType type, PackRepository repository) {
    State state = STATES.get(repository);
    return new Snapshot(
      repository.getAvailablePacks().stream().map(pack -> info(type, pack)).toList(),
      repository.getSelectedPacks().stream().map(pack -> info(type, pack)).toList(),
      state == null ? null : state.requested,
      state == null ? null : state.applied,
      null
    );
  }

  /**
   * Resolves and caches a native pack's identity for the supplied resource type.
   *
   * @param type native pack type.
   * @param pack discovered native pack.
   * @return pack identity read from its current discovery metadata.
   */
  private static PackInfo info(PackType type, Pack pack) {
    synchronized (IDENTITIES) {
      return IDENTITIES.computeIfAbsent(pack, ignored -> new EnumMap<>(PackType.class)).computeIfAbsent(type, side -> {
        try (var metadata = pack.openMetadata()) {
          return NativePackDependencies.info(side, pack.getId(), metadata);
        }
      });
    }
  }

  /**
   * Invalidates discovery metadata before native repositories refresh their pack objects.
   */
  public static void invalidateDiscovery() {
    synchronized (IDENTITIES) {
      IDENTITIES.clear();
    }
  }

  /**
   * Carries the captured selection through a native pack load.
   */
  @ApiStatus.Internal
  public static final class Request {
    /**
     * Repository whose requested and applied states this load can update.
     */
    private final PackRepository repository;

    /**
     * State identity used to ensure an older completion cannot replace a newer request.
     */
    private final State state;

    /**
     * Observations captured for callbacks in this load.
     */
    private final PackQueries queries;

    /**
     * @param repository repository supplying the request.
     * @param state state recorded for this request.
     * @param queries captured observations for this load.
     */
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
     * Clears the request if it is still current.
     * Records its order as applied on success and retains the previous applied order on failure.
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

  /**
   * Identifies a live query and export target.
   *
   * @param type native pack type.
   * @param repository live repository.
   * @param resources supplier of the current applied resource manager.
   */
  record Target(PackType type, PackRepository repository, Supplier<ResourceManager> resources) {}

  /**
   * Holds the load state associated with a native repository.
   *
   * @param requested requested order, or {@code null} outside a load.
   * @param applied last successful order, or {@code null} before a successful load.
   */
  private record State(@Nullable List<PackInfo> requested, @Nullable List<PackInfo> applied) {}

  /**
   * Holds the detached pack observations available in one view.
   *
   * @param available packs discovered by the repository, or {@code null} if unavailable.
   * @param selected native selection, or {@code null} if unavailable.
   * @param requested order requested by the active load, or {@code null} if unavailable.
   * @param applied last successfully applied order, or {@code null} if unavailable.
   * @param edited proposed editor order, or {@code null} outside an editor view.
   */
  record Snapshot(@Nullable List<PackInfo> available, @Nullable List<PackInfo> selected, @Nullable List<PackInfo> requested, @Nullable List<PackInfo> applied, @Nullable List<PackInfo> edited) {
    /**
     * Observation with every state unavailable.
     */
    private static final Snapshot EMPTY = new Snapshot(null, null, null, null, null);
  }
}
