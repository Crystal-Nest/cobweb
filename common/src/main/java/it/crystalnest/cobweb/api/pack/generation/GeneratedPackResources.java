package it.crystalnest.cobweb.api.pack.generation;

import it.crystalnest.cobweb.api.pack.generation.PackGeneration.Task;
import it.crystalnest.cobweb.api.pack.generation.output.ResourceLifetime;
import it.crystalnest.cobweb.mixin.OverlayedPackResourcesAccessor;
import it.crystalnest.cobweb.platform.Services;
import it.crystalnest.cobweb.platform.services.PackHelper.BundledResources;
import it.crystalnest.cobweb.platform.services.PackHelper.RegisteredPack;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.AbstractPackMetadataResources;
import net.minecraft.server.packs.OverlayedPackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackMetadataResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Native resource opening owned by one pack and resource load.
 */
final class GeneratedPackResources implements PackResources {
  /**
   * Registered definition, or {@code null} for an ordinary file pack wrapped to run JSON rules.
   */
  @Nullable
  final RegisteredPack definition;

  /**
   * Native resource type managed by this opening.
   */
  final PackType type;

  /**
   * Logical pack ID, or {@code null} for an ordinary pack without a declared identity.
   */
  @Nullable
  final Identifier logicalId;

  /**
   * File rules parsed from the native pack root for this opening.
   */
  FilePackRules rules = FilePackRules.EMPTY;

  /**
   * Original native pack location and known-pack identity.
   */
  private final PackLocationInfo location;

  /**
   * Shared native base and overlay openings.
   */
  @Nullable
  private final Bundles bundles;

  /**
   * Whether this opening is responsible for one bundled-resource lease.
   */
  private final boolean ownsBundles;

  /**
   * Composed native view used for lookup, listing and namespaces.
   */
  private final PackResources view;

  /**
   * Native load to which this pack opening belongs, when attached.
   */
  volatile DynamicPackLoads.Attempt attempt;

  /**
   * Current immutable index of generated entries by file identifier.
   */
  private volatile Map<Identifier, File> files;

  /**
   * References this opening owns to generated file backing.
   */
  private final PackFileOwner owned = new PackFileOwner();

  /**
   * Whether this opening has released its resources.
   */
  private boolean closed;

  /**
   * @param definition registered pack definition, or {@code null} for an ordinary native pack.
   * @param type native resource type.
   * @param logicalId logical ID, or {@code null} when undeclared.
   * @param location native pack location.
   * @param files initial generated entries.
   * @param bundles opened base and overlay layers, or {@code null} for a generated-only view.
   * @param ownsBundles whether this opening owns a lease on the bundled resources.
   */
  private GeneratedPackResources(@Nullable RegisteredPack definition, PackType type, @Nullable Identifier logicalId, PackLocationInfo location,
    Map<Identifier, File> files, @Nullable Bundles bundles, boolean ownsBundles) {
    this(definition, type, logicalId, location, files, bundles, ownsBundles, null);
  }

  /**
   * @param definition registered pack definition, or {@code null} for an ordinary native pack.
   * @param type native resource type.
   * @param logicalId logical ID, or {@code null} when undeclared.
   * @param location native pack location.
   * @param files initial generated entries.
   * @param bundles opened base and overlay layers, or {@code null} for a generated-only view.
   * @param ownsBundles whether this opening owns a lease on the bundled resources.
   * @param snapshots first-read captures to use, or {@code null} to open bundled files directly.
   */
  private GeneratedPackResources(@Nullable RegisteredPack definition, PackType type, @Nullable Identifier logicalId, PackLocationInfo location,
    Map<Identifier, File> files, @Nullable Bundles bundles, boolean ownsBundles, @Nullable StageResourceSnapshots snapshots) {
    this.definition = definition;
    this.type = type;
    this.logicalId = logicalId;
    this.location = location;
    this.files = files;
    this.bundles = bundles;
    this.ownsBundles = ownsBundles;
    PackResources content = new GeneratedContent();
    if (bundles != null) {
      if (bundles.base != null) {
        var base = bundles.base.pack();
        content = new OverlayedPackResources(snapshots == null ? base : snapshots.wrap(base), List.of(content));
      }
      if (!bundles.overlays.isEmpty()) {
        var layers = bundles.overlays.stream().map(BundledResources::pack);
        content = new OverlayedPackResources(content, (snapshots == null ? layers : layers.map(snapshots::wrap)).toList());
      }
    }
    view = content;
  }

  /**
   * Pins the visible generated map and wraps bundled reads in the stage's first-read view.
   *
   * @param snapshots first-read resource captures for the stage.
   * @return non-owning view for the current stage.
   */
  GeneratedPackResources snapshot(StageResourceSnapshots snapshots) {
    var copy = new GeneratedPackResources(definition, type, logicalId, location, files, bundles, false, snapshots);
    copy.rules = rules;
    return copy;
  }

  /**
   * Copies the opening's declarations with a different set of files and bundled layers.
   *
   * @param entries initial generated entries.
   * @param sources opened base and overlay layers, or {@code null} for a generated-only view.
   * @param owns whether the copy owns a bundled-resource lease.
   * @return new opening with independent generated-file ownership.
   */
  private GeneratedPackResources copy(Map<Identifier, File> entries, @Nullable Bundles sources, boolean owns) {
    var copy = new GeneratedPackResources(definition, type, logicalId, location, entries, sources, owns);
    copy.rules = rules;
    return copy;
  }

  /**
   * Wraps a selected ordinary pack and preserves its native base and overlay layers.
   *
   * @param type native resource type.
   * @param pack selected ordinary native pack.
   * @return generation-aware view of the ordinary pack opening.
   */
  static GeneratedPackResources wrap(PackType type, PackResources pack) {
    List<PackResources> layers = pack instanceof OverlayedPackResources overlay
      ? ((OverlayedPackResourcesAccessor) overlay).getPackResourcesStack().reversed() : List.of(pack);
    var bundles = new Bundles(nativeBundle(layers.getFirst()), layers.subList(1, layers.size()).stream().map(GeneratedPackResources::nativeBundle).toList());
    return new GeneratedPackResources(null, type, Services.PACK.packInfo(type, pack.packId(), pack).logicalId(), pack.location(), Map.of(), bundles, true);
  }

  /**
   * Adapts a native layer to the listing contract for bundled resources.
   *
   * @param pack opened native pack layer.
   * @return bundled layer with its native namespace listing.
   */
  private static BundledResources nativeBundle(PackResources pack) {
    return new BundledResources(pack, (type, namespace, output) -> pack.listResources(type, namespace, "", output));
  }

  /**
   * Identifies a pack across native openings for full-load retention.
   *
   * @return Cobweb pack instance or ordinary native pack ID.
   */
  Object ownerKey() {
    return definition == null ? packId() : definition.pack();
  }

  /**
   * Checks whether this opening has Java callbacks, file rules or retained generated files.
   *
   * @return whether the opening participates in generation.
   */
  boolean generates() {
    return definition != null && definition.generation().generates() || !rules.rules().isEmpty() || !files.isEmpty();
  }

  /**
   * Checks whether this opening exposes generated files.
   *
   * @return whether the generated index is nonempty.
   */
  boolean hasFiles() {
    return !files.isEmpty();
  }

  /**
   * Returns the registered Java callbacks for this opening.
   *
   * @return task snapshot, or an empty list for ordinary native packs.
   */
  List<Task> javaTasks() {
    return definition == null ? List.of() : definition.generation().tasks();
  }

  /**
   * Looks up a generated entry without consulting bundled layers.
   *
   * @param id namespaced file identifier.
   * @return generated file, or {@code null} when absent.
   */
  File file(Identifier id) {
    return files.get(id);
  }

  /**
   * Creates a snapshot with independent leases on this world's full-load output.
   *
   * @return opening that owns only retained full-load files.
   */
  synchronized GeneratedPackResources fullLoadSnapshot() {
    var retained = copy(Map.of(), null, false);
    retained.retainFullLoad(this);
    return retained;
  }

  /**
   * Retains the current contents of an applied pack for export.
   *
   * @return owning snapshot suitable for asynchronous export.
   * @throws IllegalStateException if the opening is closed or has not been applied.
   */
  synchronized GeneratedPackResources retainAppliedSnapshot() {
    if (closed || attempt == null || !attempt.applied()) {
      throw new IllegalStateException("Pack is not currently applied: " + packId());
    }
    if (bundles != null) {
      bundles.retain();
    }
    var snapshot = copy(files, bundles, true);
    files.values().forEach(file -> snapshot.owned.retain(file.content));
    return snapshot;
  }

  /**
   * Retains full-load entries from a previous opening and replaces the current generated index.
   *
   * @param previous opening that owns the previous full-load files.
   */
  synchronized void retainFullLoad(GeneratedPackResources previous) {
    Map<Identifier, File> retained = new HashMap<>();
    previous.files.forEach((id, file) -> {
      if (file.lifetime == ResourceLifetime.FULL_LOAD) {
        owned.retain(file.content);
        retained.put(id, file);
      }
    });
    files = Map.copyOf(retained);
  }

  /**
   * Adopts the caller's reference after validating the opening is live.
   *
   * @param file transferred backing.
   */
  synchronized void adopt(PackFile file) {
    if (closed) {
      throw new IllegalStateException("Resource pack opening has closed: " + packId());
    }
    owned.adopt(file);
  }

  /**
   * Adopts submitted references or independently retained cache references as one batch.
   *
   * @param files contributions transferred together.
   */
  synchronized void adoptBatch(List<PackContribution> files) {
    if (closed) {
      throw new IllegalStateException("Resource pack opening has closed: " + packId());
    }
    files.forEach(entry -> entry.backing().forEach(owned::adopt));
  }

  /**
   * Transfers the contents of another owner into this opening.
   *
   * @param owner owner transferring its generated backing.
   * @throws IllegalStateException if this pack opening has closed.
   */
  synchronized void adopt(PackFileOwner owner) {
    if (closed) {
      throw new IllegalStateException("Resource pack opening has closed: " + packId());
    }
    owner.transferTo(owned);
  }

  /**
   * Releases superseded ownership after readers have finished and cache owners have retained their batches.
   */
  synchronized void releaseSuperseded() {
    var visible = Collections.newSetFromMap(new IdentityHashMap<PackFile, Boolean>());
    files.values().forEach(file -> visible.add(file.content));
    owned.keep(visible);
  }

  /**
   * Updates the generated index with completed entries and sidecar removals.
   *
   * @param additions changed files by ID, with {@code null} values indicating removals.
   */
  synchronized void publish(Map<Identifier, File> additions) {
    if (closed) {
      return;
    }
    Map<Identifier, File> combined = new HashMap<>(files);
    additions.forEach((id, file) -> {
      if (file == null) {
        combined.remove(id);
      } else {
        combined.put(id, file);
      }
    });
    files = Map.copyOf(combined);
  }

  @Override
  public @NonNull PackLocationInfo location() {
    if ((!rules.rules().isEmpty() || hasFiles()) && location.knownPackInfo().isPresent()) {
      return new PackLocationInfo(location.id(), location.title(), location.source(), java.util.Optional.empty());
    }
    return location;
  }

  @Override
  public IoSupplier<InputStream> getRootResource(String @NonNull ... path) {
    return bundles == null || bundles.base == null ? null : bundles.base.pack().getRootResource(path);
  }

  @Override
  public IoSupplier<InputStream> getResource(@NonNull PackType type, @NonNull Identifier id) {
    return view.getResource(type, id);
  }

  @Override
  public void listResources(@NonNull PackType type, @NonNull String namespace, @NonNull String prefix, @NonNull ResourceOutput output) {
    view.listResources(type, namespace, prefix, output);
  }

  /**
   * Lists one effective file per identifier after applying the native overlay precedence.
   *
   * @param type native resource type.
   * @param output destination for effective resource identifiers and suppliers.
   */
  void listAll(PackType type, ResourceOutput output) {
    Map<Identifier, IoSupplier<InputStream>> resolved = new HashMap<>();
    if (bundles != null && bundles.base != null) {
      listBundle(bundles.base, type, resolved);
    }
    if (type == this.type) {
      files.forEach((id, file) -> resolved.put(id, file.content::open));
    }
    if (bundles != null) {
      bundles.overlays.forEach(overlay -> listBundle(overlay, type, resolved));
    }
    resolved.forEach(output::accept);
  }

  /**
   * Lists a bundled layer into the current map, replacing lower-priority entries.
   *
   * @param source opened bundled source.
   * @param type native resource type.
   * @param resolved effective files being collected.
   */
  private static void listBundle(BundledResources source, PackType type, Map<Identifier, IoSupplier<InputStream>> resolved) {
    source.pack().getNamespaces(type).forEach(namespace -> source.listing().list(type, namespace, resolved::put));
  }

  @Override
  public @NonNull Set<String> getNamespaces(@NonNull PackType type) {
    return view.getNamespaces(type);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T getMetadataSection(@NonNull MetadataSectionType<T> type) throws IOException {
    var primary = bundles == null || bundles.base == null ? null : bundles.base.pack();
    if (definition != null && type.equals(PackMetadataSection.forPackType(this.type))) {
      return (T) PackMetadataOverrides.read(this.type, primary, definition.description(), definition.formats());
    }
    return primary == null ? null : primary.getMetadataSection(type);
  }

  @Override
  public synchronized void close() {
    if (closed) {
      return;
    }
    closed = true;
    files = Map.of();
    owned.close();
    if (ownsBundles && bundles != null) {
      bundles.release();
    }
    if (attempt != null) {
      attempt.markClosed();
    }
  }

  /**
   * Opens a supplier over this pack's own base file for same-pack aggregation.
   *
   * @param id namespaced file identifier.
   * @param snapshots stage captures used for the read.
   * @return snapshot-backed supplier, or {@code null} when the base or file is absent.
   */
  @Nullable
  IoSupplier<InputStream> bundledFile(Identifier id, StageResourceSnapshots snapshots) {
    if (bundles == null || bundles.base == null) {
      return null;
    }
    var base = bundles.base.pack();
    return snapshots.wrap(base, type, id, base.getResource(type, id));
  }

  /**
   * Exposes generated entries and their namespaces as one native resource layer.
   */
  private final class GeneratedContent extends AbstractPackMetadataResources implements PackResources {
    private GeneratedContent() {
      super(GeneratedPackResources.this.location);
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String @NonNull ... path) {
      return null;
    }

    @Override
    public IoSupplier<InputStream> getResource(@NonNull PackType type, @NonNull Identifier id) {
      File file = type == GeneratedPackResources.this.type ? files.get(id) : null;
      return file == null ? null : file.content::open;
    }

    @Override
    public void listResources(@NonNull PackType type, @NonNull String namespace, @NonNull String prefix, @NonNull ResourceOutput output) {
      if (type == GeneratedPackResources.this.type) {
        String directory = prefix.isEmpty() ? "" : prefix + "/";
        files.forEach((id, file) -> {
          if (id.getNamespace().equals(namespace) && id.getPath().startsWith(directory)) {
            output.accept(id, file.content::open);
          }
        });
      }
    }

    @Override
    public @NonNull Set<String> getNamespaces(@NonNull PackType type) {
      if (type != GeneratedPackResources.this.type) {
        return Set.of();
      }
      var namespaces = new HashSet<>(rules.namespaces());
      if (definition != null && definition.generation().generates()) {
        namespaces.addAll(definition.generation().namespaces);
      }
      files.keySet().forEach(id -> namespaces.add(id.getNamespace()));
      return namespaces;
    }

    @Override
    public void close() {}
  }

  /**
   * Shares opened native base and overlay layers between independently owned snapshots.
   */
  private static final class Bundles {
    /**
     * Optional native base layer, also used for pack-root resources.
     */
    @Nullable
    private final BundledResources base;

    /**
     * Active native overlay layers, in application order.
     */
    private final List<BundledResources> overlays;

    /**
     * Number of openings with a lease on the bundled layers.
     */
    private int users = 1;

    /**
     * @param base native base layer, or {@code null} when the pack has no bundled base.
     * @param overlays active overlays in application order.
     */
    private Bundles(@Nullable BundledResources base, List<BundledResources> overlays) {
      this.base = base;
      this.overlays = overlays;
    }

    /**
     * Acquires another lease on the bundled layers.
     */
    private synchronized void retain() {
      users++;
    }

    /**
     * Releases one lease and closes the bundled layers when the last owner releases them.
     */
    private synchronized void release() {
      if (--users == 0) {
        if (base != null) {
          base.pack().close();
        }
        overlays.forEach(overlay -> overlay.pack().close());
      }
    }
  }

  /**
   * Describes generated file backing and its ownership semantics.
   *
   * @param content managed file backing.
   * @param lifetime native lifetime of the file.
   * @param companion whether the file is owned by a grouped sidecar operation.
   */
  record File(PackFile content, ResourceLifetime lifetime, boolean companion) {
    /**
     * @param content managed file backing.
     * @param lifetime native lifetime of the file.
     */
    File(PackFile content, ResourceLifetime lifetime) {
      this(content, lifetime, false);
    }
  }

  /**
   * Opens a Cobweb registration as native pack resources.
   *
   * @param definition registered pack definition.
   */
  record Supplier(RegisteredPack definition) implements Pack.ResourcesSupplier {
    @Override
    public @NonNull PackMetadataResources openMetadata(@NonNull PackLocationInfo location) {
      return open(location, List.of());
    }

    @Override
    public @NonNull Stream<PackResources> openResources(@NonNull PackLocationInfo location, Pack.Metadata metadata) {
      return Stream.of(open(location, metadata.overlays()));
    }

    /**
     * Opens bundled resources using the native metadata's active overlay directories.
     *
     * @param location native pack location.
     * @param overlays native overlay directory names.
     * @return owning generation-aware pack opening.
     */
    private GeneratedPackResources open(PackLocationInfo location, List<String> overlays) {
      var base = bundled(location, definition.bundledRoot());
      var active = overlays.stream().map(path -> {
        if (path.equals(".") || path.equals("..")) {
          throw new IllegalArgumentException("Overlay must name a directory inside " + definition.bundledRoot() + ": " + path);
        }
        return bundled(location, definition.bundledRoot() + "/" + path);
      }).filter(Objects::nonNull).toList();
      return new GeneratedPackResources(definition, definition.pack().type(), definition.declaration().id(), location, Map.of(), new Bundles(base, active), true);
    }

    /**
     * Opens a bundled layer through the current loader's pack service.
     *
     * @param location native pack location.
     * @param path mod-relative path to the bundled layer.
     * @return bundled layer, or {@code null} when its root is absent.
     */
    @Nullable
    private BundledResources bundled(PackLocationInfo location, String path) {
      return Services.PACK.openBundledPack(location, definition.declaration().id().getNamespace(), path);
    }
  }
}
