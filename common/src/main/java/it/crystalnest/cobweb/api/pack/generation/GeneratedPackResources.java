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

  final PackType type;

  @Nullable
  final Identifier logicalId;

  FilePackRules rules = FilePackRules.EMPTY;

  private final PackLocationInfo location;

  @Nullable
  private final Bundles bundles;

  private final boolean ownsBundles;

  private final PackResources view;

  volatile DynamicPackLoads.Attempt attempt;

  private volatile Map<Identifier, File> files;

  private final PackFileOwner owned = new PackFileOwner();

  private boolean closed;

  private GeneratedPackResources(@Nullable RegisteredPack definition, PackType type, @Nullable Identifier logicalId, PackLocationInfo location,
    Map<Identifier, File> files, @Nullable Bundles bundles, boolean ownsBundles) {
    this(definition, type, logicalId, location, files, bundles, ownsBundles, null);
  }

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

  GeneratedPackResources snapshot(StageResourceSnapshots snapshots) {
    var copy = new GeneratedPackResources(definition, type, logicalId, location, files, bundles, false, snapshots);
    copy.rules = rules;
    return copy;
  }

  private GeneratedPackResources copy(Map<Identifier, File> entries, @Nullable Bundles sources, boolean owns) {
    var copy = new GeneratedPackResources(definition, type, logicalId, location, entries, sources, owns);
    copy.rules = rules;
    return copy;
  }

  static GeneratedPackResources wrap(PackType type, PackResources pack) {
    List<PackResources> layers = pack instanceof OverlayedPackResources overlay
      ? ((OverlayedPackResourcesAccessor) overlay).getPackResourcesStack().reversed() : List.of(pack);
    var bundles = new Bundles(nativeBundle(layers.getFirst()), layers.subList(1, layers.size()).stream().map(GeneratedPackResources::nativeBundle).toList());
    return new GeneratedPackResources(null, type, Services.PACK.packInfo(type, pack.packId(), pack).logicalId(), pack.location(), Map.of(), bundles, true);
  }

  private static BundledResources nativeBundle(PackResources pack) {
    return new BundledResources(pack, (type, namespace, output) -> pack.listResources(type, namespace, "", output));
  }

  Object ownerKey() {
    return definition == null ? packId() : definition.pack();
  }

  boolean generates() {
    return definition != null && definition.generation().generates() || !rules.rules().isEmpty() || !files.isEmpty();
  }

  boolean hasFiles() {
    return !files.isEmpty();
  }

  List<Task> javaTasks() {
    return definition == null ? List.of() : definition.generation().tasks();
  }

  File file(Identifier id) {
    return files.get(id);
  }

  synchronized GeneratedPackResources fullLoadSnapshot() {
    var retained = copy(Map.of(), null, false);
    retained.retainFullLoad(this);
    return retained;
  }

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

  @Nullable
  IoSupplier<InputStream> bundledFile(Identifier id, StageResourceSnapshots snapshots) {
    if (bundles == null || bundles.base == null) {
      return null;
    }
    var base = bundles.base.pack();
    return snapshots.wrap(base, type, id, base.getResource(type, id));
  }

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

  private static final class Bundles {
    @Nullable
    private final BundledResources base;

    private final List<BundledResources> overlays;

    private int users = 1;

    private Bundles(@Nullable BundledResources base, List<BundledResources> overlays) {
      this.base = base;
      this.overlays = overlays;
    }

    private synchronized void retain() {
      users++;
    }

    private synchronized void release() {
      if (--users == 0) {
        if (base != null) {
          base.pack().close();
        }
        overlays.forEach(overlay -> overlay.pack().close());
      }
    }
  }

  record File(PackFile content, ResourceLifetime lifetime, boolean companion) {
    File(PackFile content, ResourceLifetime lifetime) {
      this(content, lifetime, false);
    }
  }

  record Supplier(RegisteredPack definition) implements Pack.ResourcesSupplier {
    @Override
    public @NonNull PackMetadataResources openMetadata(@NonNull PackLocationInfo location) {
      return open(location, List.of());
    }

    @Override
    public @NonNull Stream<PackResources> openResources(@NonNull PackLocationInfo location, Pack.Metadata metadata) {
      return Stream.of(open(location, metadata.overlays()));
    }

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

    @Nullable
    private BundledResources bundled(PackLocationInfo location, String path) {
      return Services.PACK.openBundledPack(location, definition.declaration().id().getNamespace(), path);
    }
  }
}
