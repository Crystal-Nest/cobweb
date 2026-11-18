package it.crystalnest.cobweb.api.pack.generation;

import it.crystalnest.cobweb.Constants;
import it.crystalnest.cobweb.api.pack.generation.lifecycle.LoadKind;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

/**
 * Correlates diagnostics across a native resource load, including preparation before generation opens.
 */
@ApiStatus.Internal
public final class PackLoadLog {
  /**
   * Assigns process-local load IDs.
   */
  private static final AtomicLong nextId = new AtomicLong();

  /**
   * Carries a full-load trace through Minecraft's synchronous pack-opening operation.
   */
  private static final ScopedValue<PackLoadLog> fullLoad = ScopedValue.newInstance();

  /**
   * Prefix shared by all diagnostics for this load.
   */
  private final String label;

  /**
   * Time the native load was requested.
   */
  private final long startedAt = System.nanoTime();

  /**
   * Whether the selected stack includes Cobweb work worth tracing.
   */
  private final AtomicBoolean traced = new AtomicBoolean();

  /**
   * Whether a terminal outcome has already been logged.
   */
  private final AtomicBoolean finished = new AtomicBoolean();

  /**
   * Java callbacks reached by the load, including cache hits.
   */
  private final LongAdder callbacks = new LongAdder();

  /**
   * JSON rules reached by the load, including cache hits.
   */
  private final LongAdder rules = new LongAdder();

  /**
   * Tasks fulfilled from a reusable contribution.
   */
  private final LongAdder cacheHits = new LongAdder();

  /**
   * Native pack count reported by preparation.
   */
  private volatile int packCount;

  /**
   * Selected IDs in native priority order, captured when debug logging is enabled.
   */
  private List<String> selected = List.of();

  /**
   * Required packs restored before the native selection is opened.
   */
  private final List<String> restored = new ArrayList<>();

  /**
   * Whether pack preparation or validation failed before native application.
   */
  private volatile boolean validationFailed;

  /**
   * @param type native pack type.
   * @param kind type of resource load.
   */
  public PackLoadLog(PackType type, LoadKind kind) {
    label = "Pack load #" + nextId.incrementAndGet() + " (" + type + "/" + kind + ")";
  }

  /**
   * Prepares a full-load resource manager with this trace available to its pack-opening hook.
   *
   * @param prepare synchronous native preparation.
   * @param <T> prepared resource manager result type.
   * @return native preparation result.
   */
  public <T> T prepareFullLoad(Supplier<T> prepare) {
    return ScopedValue.where(fullLoad, this).call(prepare::get);
  }

  /**
   * Finds the current full-load trace or creates one for a standalone pack opening.
   *
   * @return trace for this resource opening.
   */
  public static PackLoadLog fullLoad() {
    return fullLoad.isBound() ? fullLoad.get() : new PackLoadLog(PackType.SERVER_DATA, LoadKind.FULL_LOAD);
  }

  /**
   * Captures the selected stack before it is adapted for generation.
   *
   * @param packs selected packs in increasing native priority.
   */
  void select(List<PackResources> packs) {
    packCount = packs.size();
    if (Constants.LOGGER.isDebugEnabled()) {
      selected = packs.stream().map(PackResources::packId).toList();
    }
  }

  /**
   * Starts tracing once the load encounters a Cobweb pack, rule, or validation failure.
   */
  void start() {
    if (traced.compareAndSet(false, true)) {
      Constants.LOGGER.debug("{} started; selected packs (lowest priority first): {}", label, selected);
      if (!restored.isEmpty()) {
        Constants.LOGGER.debug("{} restored always-active packs {}", label, restored);
      }
    }
  }

  /**
   * Records a required pack inserted before native pack opening.
   *
   * @param id native ID of the restored pack.
   */
  public void restoredRequired(String id) {
    if (Constants.LOGGER.isDebugEnabled()) {
      restored.add(id);
    }
  }

  /**
   * Identifies an early validation failure and reports the load outcome.
   *
   * @param failure preparation or validation failure.
   */
  void validationFailed(Throwable failure) {
    validationFailed = true;
    start();
    finish(failure);
  }

  /**
   * Traces a decision or transition for an active load.
   *
   * @param message event details.
   */
  void debug(String message) {
    if (traced.get()) {
      Constants.LOGGER.debug("{} {}", label, message);
    }
  }

  /**
   * Identifies the native continuation after Cobweb's initial generation stages.
   */
  public void handoff() {
    debug("handed off to Minecraft after initial generation");
  }

  /**
   * Returns the prefix for related generation diagnostics.
   *
   * @return load ID with native pack type and load kind.
   */
  String label() {
    return label;
  }

  /**
   * Records a task that reached generation or cache lookup.
   *
   * @param fileRule whether the task is a JSON rule.
   */
  void task(boolean fileRule) {
    (fileRule ? rules : callbacks).increment();
  }

  /**
   * Records a successful cache replay.
   */
  void cacheHit() {
    cacheHits.increment();
  }

  /**
   * Explains why an opted-in task regenerated instead of reusing its contribution.
   *
   * @param pack pack supplying the task.
   * @param task task attempting to reuse a previous contribution.
   * @param reason reason reuse was unavailable.
   */
  void cacheMiss(GeneratedPackResources pack, PackGeneration.Task task, String reason) {
    if (Constants.LOGGER.isDebugEnabled()) {
      Constants.LOGGER.debug("{} cache miss for {} {} in {}: {}", label, task.fileRule() ? "rule" : "callback", task.index(), pack.packId(), reason);
    }
  }

  /**
   * Reports one terminal outcome with available generation counts.
   *
   * @param failure native failure, or {@code null} on success.
   */
  void finish(@Nullable Throwable failure) {
    if (!traced.get() || !finished.compareAndSet(false, true)) {
      return;
    }
    while (failure instanceof CompletionException && failure.getCause() != null) {
      failure = failure.getCause();
    }
    boolean cancelled = failure instanceof CancellationException;
    String result;
    if (cancelled) {
      result = "cancelled";
    } else if (failure == null) {
      result = "applied";
    } else {
      result = (validationFailed ? "failed validation" : "failed") + " (" + failure.getClass().getSimpleName() + ")";
    }
    long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    long callbackCount = callbacks.sum();
    long ruleCount = rules.sum();
    String counts = "packs=" + packCount + ", callbacks=" + callbackCount + ", rules=" + ruleCount + ", cache hits=" + cacheHits.sum();
    if (cancelled || failure == null && callbackCount + ruleCount == 0 && !validationFailed) {
      Constants.LOGGER.debug("{} {} in {} ms ({})", label, result, elapsed, counts);
    } else if (failure == null) {
      Constants.LOGGER.info("{} {} in {} ms ({})", label, result, elapsed, counts);
    } else {
      Constants.LOGGER.error("{} {} in {} ms ({})", label, result, elapsed, counts);
    }
  }
}
