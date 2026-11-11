package it.crystalnest.cobweb.api.pack;

import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import it.crystalnest.cobweb.Constants;
import it.crystalnest.cobweb.api.pack.client.AssetGenerationResources;
import it.crystalnest.cobweb.api.registry.CobwebDataPackRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.flag.FeatureFlagSet;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scoped, read-only inputs to one generation callback.
 */
public final class GenerationContext implements CallbackRegistration.CapturedValues {
  private final GenerationResources resources;

  private final Identifier pack;

  private final Phase phase;

  private final Stage stage;

  private final LoadKind loadKind;

  private final HolderLookup.Provider registries;

  private final Map<CapturedInput<?>, Object> captured;

  private final Map<TagKey<?>, List<? extends Holder<?>>> tags;

  private final CacheObservations observations;

  private final PackQueries packs;

  private final FeatureFlagSet features;

  private final Set<ScopedStream> streams = ConcurrentHashMap.newKeySet();

  private volatile boolean open = true;

  GenerationContext(ResourceManager manager, Identifier pack, Phase phase, Stage stage, LoadKind loadKind, HolderLookup.Provider registries,
    Map<CapturedInput<?>, Object> captured, Map<TagKey<?>, List<? extends Holder<?>>> tags, CacheObservations observations, PackQueries packs, FeatureFlagSet features) {
    resources = new GenerationResources(this, manager);
    this.pack = pack;
    this.phase = phase;
    this.stage = stage;
    this.loadKind = loadKind;
    this.registries = registries;
    this.captured = captured;
    this.tags = tags;
    this.observations = observations;
    this.packs = packs;
    this.features = features;
  }

  /**
   * Returns the pack that owns the callback.
   *
   * @return logical pack identity.
   */
  public Identifier pack() {
    return pack;
  }

  /**
   * Returns the current native generation phase.
   *
   * @return active phase.
   */
  public Phase phase() {
    return phase;
  }

  /**
   * Returns the stage in which this callback is running.
   *
   * @return active stage.
   */
  public Stage stage() {
    return stage;
  }

  /**
   * Returns the current load kind.
   *
   * @return full load or reload.
   */
  public LoadKind loadKind() {
    return loadKind;
  }

  /**
   * Returns the stable combined resource view for this callback.
   *
   * @return selected sources and visible completed generation.
   */
  public GenerationResources resources() {
    checkOpen();
    return resources;
  }

  /**
   * Returns the typed native data view for this callback.
   *
   * @return scoped server data inputs.
   * @throws IllegalStateException if this callback generates client assets or has ended.
   */
  public DataGenerationResources data() {
    checkData();
    return new DataGenerationResources(this);
  }

  /**
   * Returns the typed client asset view for this callback.
   *
   * @return scoped client asset inputs.
   * @throws IllegalStateException if this callback generates server data or has ended.
   */
  public AssetGenerationResources assets() {
    checkAssets();
    return new AssetGenerationResources(this);
  }

  /**
   * Returns the native pack observations captured for this load.
   *
   * @return read-only snapshot of repository selection and priority.
   */
  public PackQueries packs() {
    checkOpen();
    observe(CacheObservations.Kind.PACKS, "");
    return packs;
  }

  /**
   * Returns a value captured at the beginning of this load.
   *
   * @param input captured value declaration.
   * @param <T> value type.
   * @return captured value.
   * @throws IllegalArgumentException if the input was not declared.
   */
  @SuppressWarnings("unchecked")
  @Override
  public <T> T value(CapturedInput<T> input) {
    checkOpen();
    if (!captured.containsKey(input)) {
      throw new IllegalArgumentException("Captured input was not declared for " + pack);
    }
    return (T) captured.get(input);
  }

  /**
   * Looks up a data pack registry in the current native context.
   *
   * @param definition registry definition.
   * @param <T> entry type.
   * @return native read-only lookup for this phase.
   */
  public <T> HolderLookup.RegistryLookup<T> registry(CobwebDataPackRegistry<T> definition) {
    return registry(definition.key());
  }

  /**
   * Looks up a native registry by key.
   *
   * @param key native registry key.
   * @param <T> entry type.
   * @return native read-only lookup for this phase.
   * @throws IllegalStateException if this phase has no lookup for the key.
   */
  public <T> HolderLookup.RegistryLookup<T> registry(ResourceKey<? extends Registry<T>> key) {
    checkData();
    return registries().lookup(key).orElseThrow(() -> new IllegalStateException("Registry " + key.identifier() + " unavailable in " + phase));
  }

  /**
   * Reads members decoded by native pending tag preparation for this load.
   *
   * @param tag native tag key.
   * @param <T> member type.
   * @return prepared members or an empty list if the prepared tag is absent.
   * @throws IllegalStateException if native preparation has not completed.
   */
  @SuppressWarnings("unchecked")
  public <T> List<Holder<T>> preparedTag(TagKey<T> tag) {
    checkData();
    observations.unsupported();
    if (phase == Phase.DATA_RESOURCES || phase == Phase.CLIENT_RESOURCES) {
      throw new IllegalStateException("Prepared tags are unavailable in " + phase);
    }
    return (List<Holder<T>>) (List<?>) tags.getOrDefault(tag, List.of());
  }

  HolderLookup.Provider registries() {
    checkOpen();
    observations.unsupported();
    return registries;
  }

  FeatureFlagSet features() {
    checkData();
    observe(CacheObservations.Kind.FEATURES, "");
    return features;
  }

  void untrackedRead() {
    checkOpen();
    observations.unsupported();
  }

  DynamicOps<JsonElement> jsonOps() {
    checkOpen();
    return phase.type() == PackType.CLIENT_RESOURCES ? JsonOps.INSTANCE : RegistryOps.create(JsonOps.INSTANCE, registries());
  }

  void observe(CacheObservations.Kind kind, String key) {
    observations.observe(kind, key);
  }

  synchronized InputStream stream(InputStream input) throws IOException {
    if (!open) {
      input.close();
      checkOpen();
    }
    var scoped = new ScopedStream(input);
    streams.add(scoped);
    return scoped;
  }

  synchronized void close() {
    open = false;
    for (var stream : streams) {
      try {
        stream.close();
      } catch (IOException failure) {
        Constants.LOGGER.warn("Unable to close an input stream of {}", pack, failure);
      }
    }
  }

  void checkOpen() {
    if (!open) {
      throw new IllegalStateException("Generation callback has ended for " + pack);
    }
  }

  void checkAssets() {
    checkOpen();
    if (phase.type() != PackType.CLIENT_RESOURCES) {
      throw new IllegalStateException("Client assets are unavailable in " + phase);
    }
  }

  void checkData() {
    checkOpen();
    if (phase.type() != PackType.SERVER_DATA) {
      throw new IllegalStateException("Server data inputs are unavailable in " + phase);
    }
  }

  private final class ScopedStream extends FilterInputStream {
    private volatile boolean closed;

    private ScopedStream(InputStream input) {
      super(input);
    }

    @Override
    public int read() throws IOException {
      checkStream();
      return super.read();
    }

    @Override
    public int read(byte[] target, int offset, int size) throws IOException {
      checkStream();
      return super.read(target, offset, size);
    }

    @Override
    public long skip(long size) throws IOException {
      checkStream();
      return super.skip(size);
    }

    @Override
    public int available() throws IOException {
      checkStream();
      return super.available();
    }

    @Override
    public void reset() throws IOException {
      checkStream();
      super.reset();
    }

    @Override
    public synchronized void close() throws IOException {
      if (!closed) {
        closed = true;
        streams.remove(this);
        super.close();
      }
    }

    private void checkStream() throws IOException {
      if (closed || !open) {
        throw new IOException("Generation input stream has closed for " + pack);
      }
    }
  }
}
