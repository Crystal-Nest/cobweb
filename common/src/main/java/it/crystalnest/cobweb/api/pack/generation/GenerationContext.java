package it.crystalnest.cobweb.api.pack.generation;

import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import it.crystalnest.cobweb.Constants;
import it.crystalnest.cobweb.api.pack.generation.input.CapturedInput;
import it.crystalnest.cobweb.api.pack.generation.lifecycle.LoadKind;
import it.crystalnest.cobweb.api.pack.generation.lifecycle.Phase;
import it.crystalnest.cobweb.api.pack.generation.lifecycle.Stage;
import it.crystalnest.cobweb.api.pack.query.PackQueries;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides read-only inputs to one generation callback.
 * Resource and native lookup operations are scoped to the callback. For an asynchronous callback, the scope lasts until its returned completion stage finishes.
 * Read resources through {@link #resources()} and submit changes through {@link GenerationOutput}.
 */
public final class GenerationContext implements CallbackRegistration.CapturedValues {
  /**
   * The callback's stable view of resolved resource files.
   */
  private final GenerationResources resources;

  /**
   * The logical ID of the pack that owns the callback.
   */
  private final Identifier pack;

  /**
   * The native boundary where the callback runs.
   */
  private final Phase phase;

  /**
   * The generation stage inside the current phase.
   */
  private final Stage stage;

  /**
   * Whether the native load is a full load or reload.
   */
  private final LoadKind loadKind;

  /**
   * Native lookups available at the current boundary.
   */
  private final HolderLookup.Provider registries;

  /**
   * The declared Java values captured at the beginning of this load.
   */
  private final Map<CapturedInput<?>, Object> captured;

  /**
   * The native prepared tag members available at the current boundary.
   */
  private final Map<TagKey<?>, List<? extends Holder<?>>> tags;

  /**
   * Cache observations made by this callback.
   */
  private final CacheObservations observations;

  /**
   * The captured pack selection and priority for this load.
   */
  private final PackQueries packs;

  /**
   * The feature flags active for this load.
   */
  private final FeatureFlagSet features;

  /**
   * Input streams that are still owned by the callback.
   */
  private final Set<ScopedStream> streams = ConcurrentHashMap.newKeySet();

  /**
   * Whether scoped inputs can still be used.
   */
  private volatile boolean open = true;

  /**
   * @param manager stable native resource view.
   * @param pack logical ID of the pack that owns the callback.
   * @param phase current native generation phase.
   * @param stage stage within the current phase.
   * @param loadKind full load or reload.
   * @param registries available registry lookups.
   * @param captured declared values captured at load start.
   * @param tags native prepared tag members.
   * @param observations cache observation tracker.
   * @param packs pack selection and priority snapshot.
   * @param features active feature flags.
   */
  GenerationContext(ResourceManager manager, Identifier pack, Phase phase, Stage stage, LoadKind loadKind, HolderLookup.Provider registries,
    Map<CapturedInput<?>, Object> captured, Map<TagKey<?>, List<? extends Holder<?>>> tags, CacheObservations observations, PackQueries packs, FeatureFlagSet features) {
    resources = new GenerationResources(this, manager, observations);
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
   * The view includes the selected resources, completed earlier stages and completed same-stage output from this pack's generation predecessors.
   * Minecraft's pack priority still determines which visible file wins.
   *
   * @return selected sources and visible completed generation.
   * @throws IllegalStateException if the callback has ended.
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
   * @return read-only snapshot of the requested pack selection and priority for this load.
   * @throws IllegalStateException if the callback has ended.
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
   * @throws IllegalStateException if the callback has ended.
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
   * Looks up a native registry by key.
   *
   * @param key native registry key.
   * @param <T> entry type.
   * @return native read-only lookup for this phase.
   * @throws IllegalStateException if the callback has ended, runs for client assets or this phase has no lookup for the key.
   */
  <T> HolderLookup.RegistryLookup<T> registry(ResourceKey<? extends Registry<T>> key) {
    checkData();
    return registries().lookup(key).orElseThrow(() -> new IllegalStateException("Registry " + key.identifier() + " unavailable in " + phase));
  }

  /**
   * Reads members decoded by native pending tag preparation for this load.
   *
   * @param tag native tag key.
   * @param <T> member type.
   * @return prepared members or an empty list if the prepared tag is absent.
   * @throws IllegalStateException if the callback has ended, runs for client assets or native tag preparation has not completed.
   */
  @SuppressWarnings("unchecked")
  <T> List<Holder<T>> preparedTag(TagKey<T> tag) {
    checkData();
    observations.unsupported();
    if (phase == Phase.DATA_RESOURCES || phase == Phase.CLIENT_RESOURCES) {
      throw new IllegalStateException("Prepared tags are unavailable in " + phase);
    }
    return (List<Holder<T>>) (List<?>) tags.getOrDefault(tag, List.of());
  }

  /**
   * Returns native registry access and disables cache reuse for the callback's untracked registry read.
   *
   * @return registry lookups currently available.
   */
  HolderLookup.Provider registries() {
    checkOpen();
    observations.unsupported();
    return registries;
  }

  /**
   * Returns the active server feature flags and records the observation for caching.
   *
   * @return feature flags for this attempt.
   */
  FeatureFlagSet features() {
    checkData();
    observe(CacheObservations.Kind.FEATURES, "");
    return features;
  }

  /**
   * Disables cache reuse when the callback reads an input without a stable fingerprint.
   */
  void untrackedRead() {
    checkOpen();
    observations.unsupported();
  }

  /**
   * Returns JSON operations for the current side. On server data, registry lookups through these operations disable cache reuse.
   *
   * @return plain client JSON operations or registry-aware server JSON operations.
   */
  DynamicOps<JsonElement> jsonOps() {
    checkOpen();
    return phase.type() == PackType.CLIENT_RESOURCES ? JsonOps.INSTANCE : RegistryOps.create(JsonOps.INSTANCE, new RegistryOps.RegistryInfoLookup() {
      @Override
      public <T> Optional<HolderGetter<T>> lookup(ResourceKey<? extends Registry<? extends T>> key) {
        checkOpen();
        observations.unsupported();
        return registries.lookup(key).map(value -> value);
      }
    });
  }

  /**
   * Records an observable native input for cache validation.
   *
   * @param kind kind of observed input.
   * @param key identifier or directory associated with the observation.
   */
  void observe(CacheObservations.Kind kind, String key) {
    observations.observe(kind, key);
  }

  /**
   * Ties an opened stream to the lifetime of this callback.
   *
   * @param input already-open input stream to take ownership of.
   * @return a stream that closes with the callback.
   * @throws IOException if the input cannot be closed after the callback has ended.
   */
  synchronized InputStream stream(InputStream input) throws IOException {
    if (!open) {
      input.close();
      checkOpen();
    }
    var scoped = new ScopedStream(input);
    streams.add(scoped);
    return scoped;
  }

  /**
   * Ends the callback scope and closes any remaining input streams.
   */
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

  /**
   * Checks that the caller is still inside the callback scope.
   *
   * @throws IllegalStateException if the callback has ended.
   */
  void checkOpen() {
    if (!open) {
      throw new IllegalStateException("Generation callback has ended for " + pack);
    }
  }

  /**
   * Checks that the callback is active and runs for client resources.
   *
   * @throws IllegalStateException if the callback has ended or does not run for client resources.
   */
  void checkAssets() {
    checkOpen();
    if (phase.type() != PackType.CLIENT_RESOURCES) {
      throw new IllegalStateException("Client assets are unavailable in " + phase);
    }
  }

  /**
   * Checks that the callback is active and runs for server data.
   *
   * @throws IllegalStateException if the callback has ended or does not run for server data.
   */
  void checkData() {
    checkOpen();
    if (phase.type() != PackType.SERVER_DATA) {
      throw new IllegalStateException("Server data inputs are unavailable in " + phase);
    }
  }

  /**
   * Closes a resource stream with its owning callback and rejects subsequent reads.
   */
  private final class ScopedStream extends FilterInputStream {
    /**
     * Whether this stream's underlying resource has been closed.
     */
    private volatile boolean closed;

    /**
     * @param input resource stream owned by the wrapper.
     */
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

    /**
     * Checks that the stream and its owning callback are still open.
     *
     * @throws IOException if the stream or callback has closed.
     */
    private void checkStream() throws IOException {
      if (closed || !open) {
        throw new IOException("Generation input stream has closed for " + pack);
      }
    }
  }
}
