package it.crystalnest.cobweb.api.pack;

import it.crystalnest.cobweb.impl.pack.PackMetadataOverrides;
import it.crystalnest.cobweb.platform.Services;
import it.crystalnest.cobweb.platform.services.BundledPackResources;
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

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Native resource opening owned by one pack and resource load.
 */
final class GeneratedPackResources implements PackResources {
  final ResourcePack definition;

  private final PackLocationInfo location;

  @Nullable
  private final Bundles bundles;

  private final boolean ownsBundles;

  private final PackResources view;

  volatile DynamicPackLoads.Attempt attempt;

  private volatile Map<Identifier, File> files;

  private final Set<PackFile> owned = Collections.newSetFromMap(new IdentityHashMap<>());

  private boolean closed;

  GeneratedPackResources(ResourcePack definition, PackLocationInfo location, Map<Identifier, File> files, @Nullable Bundles bundles, boolean ownsBundles) {
    this.definition = definition;
    this.location = location;
    this.files = files;
    this.bundles = bundles;
    this.ownsBundles = ownsBundles;
    PackResources content = new GeneratedContent();
    if (bundles != null) {
      if (bundles.base != null) {
        content = definition.generates() ? new OverlayedPackResources(bundles.base.pack(), List.of(content)) : bundles.base.pack();
      }
      if (!bundles.overlays.isEmpty()) {
        content = new OverlayedPackResources(content, bundles.overlays.stream().map(BundledPackResources::pack).toList());
      }
    }
    view = content;
  }

  GeneratedPackResources snapshot() {
    return new GeneratedPackResources(definition, location, files, bundles, false);
  }

  File file(Identifier id) {
    return files.get(id);
  }

  synchronized GeneratedPackResources fullLoadSnapshot() {
    var retained = new GeneratedPackResources(definition, location, Map.of(), null, false);
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
    var snapshot = new GeneratedPackResources(definition, location, files, bundles, true);
    files.values().forEach(file -> {
      if (snapshot.owned.add(file.content)) {
        file.content.retain();
      }
    });
    return snapshot;
  }

  synchronized void retainFullLoad(GeneratedPackResources previous) {
    Map<Identifier, File> retained = new HashMap<>();
    previous.files.forEach((id, file) -> {
      if (file.lifetime == ResourceLifetime.FULL_LOAD) {
        if (owned.add(file.content)) {
          file.content.retain();
        }
        retained.put(id, file);
      }
    });
    files = Map.copyOf(retained);
  }

  synchronized void own(PackFile file) {
    if (closed) {
      throw new IllegalStateException("Resource pack opening has closed: " + packId());
    }
    owned.add(file);
  }

  synchronized void ownBatch(List<CallbackCache.Contribution> files) {
    if (closed) {
      throw new IllegalStateException("Resource pack opening has closed: " + packId());
    }
    for (var entry : files) {
      if (entry.file() != null) {
        owned.add(entry.file());
      }
      if (entry.companion() != null) {
        owned.add(entry.companion());
      }
    }
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
  public PackLocationInfo location() {
    return location;
  }

  @Override
  public IoSupplier<InputStream> getRootResource(String... path) {
    return bundles == null || bundles.base == null ? null : bundles.base.pack().getRootResource(path);
  }

  @Override
  public IoSupplier<InputStream> getResource(PackType type, Identifier id) {
    return view.getResource(type, id);
  }

  @Override
  public void listResources(PackType type, String namespace, String prefix, ResourceOutput output) {
    view.listResources(type, namespace, prefix, output);
  }

  void listAll(PackType type, ResourceOutput output) {
    Map<Identifier, IoSupplier<InputStream>> resolved = new HashMap<>();
    if (bundles != null && bundles.base != null) {
      listBundle(bundles.base, type, resolved);
    }
    if (type == definition.type()) {
      files.forEach((id, file) -> resolved.put(id, file.content::open));
    }
    if (bundles != null) {
      bundles.overlays.forEach(overlay -> listBundle(overlay, type, resolved));
    }
    resolved.forEach(output::accept);
  }

  private static void listBundle(BundledPackResources source, PackType type, Map<Identifier, IoSupplier<InputStream>> resolved) {
    source.pack().getNamespaces(type).forEach(namespace -> source.listing().list(type, namespace, resolved::put));
  }

  @Override
  public Set<String> getNamespaces(PackType type) {
    return view.getNamespaces(type);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T getMetadataSection(MetadataSectionType<T> type) throws IOException {
    var primary = bundles == null || bundles.base == null ? null : bundles.base.pack();
    if (type.equals(PackMetadataSection.forPackType(definition.type()))) {
      return (T) PackMetadataOverrides.read(definition.type(), primary, definition.description(), definition.formats());
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
    owned.forEach(PackFile::release);
    owned.clear();
    if (ownsBundles && bundles != null) {
      bundles.release();
    }
    if (attempt != null) {
      attempt.markClosed();
    }
  }

  @Nullable
  IoSupplier<InputStream> bundledFile(Identifier id) {
    return bundles == null || bundles.base == null ? null : bundles.base.pack().getResource(definition.type(), id);
  }

  private final class GeneratedContent extends AbstractPackMetadataResources implements PackResources {
    private GeneratedContent() {
      super(GeneratedPackResources.this.location);
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... path) {
      return null;
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, Identifier id) {
      File file = type == definition.type() ? files.get(id) : null;
      return file == null ? null : file.content::open;
    }

    @Override
    public void listResources(PackType type, String namespace, String prefix, ResourceOutput output) {
      if (type == definition.type()) {
        String directory = prefix.isEmpty() ? "" : prefix + "/";
        files.forEach((id, file) -> {
          if (id.getNamespace().equals(namespace) && id.getPath().startsWith(directory)) {
            output.accept(id, file.content::open);
          }
        });
      }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
      return type == definition.type() ? definition.namespaces() : Set.of();
    }

    @Override
    public void close() {}
  }

  private static final class Bundles {
    @Nullable
    private final BundledPackResources base;

    private final List<BundledPackResources> overlays;

    private int users = 1;

    private Bundles(@Nullable BundledPackResources base, List<BundledPackResources> overlays) {
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

  record Supplier(ResourcePack definition) implements Pack.ResourcesSupplier {
    @Override
    public PackMetadataResources openMetadata(PackLocationInfo location) {
      return open(location, List.of());
    }

    @Override
    public Stream<PackResources> openResources(PackLocationInfo location, Pack.Metadata metadata) {
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
      return new GeneratedPackResources(definition, location, Map.of(), new Bundles(base, active), true);
    }

    @Nullable
    private BundledPackResources bundled(PackLocationInfo location, String path) {
      return Services.REGISTRY.openBundledPack(location, definition.identifier().getNamespace(), path);
    }
  }
}
