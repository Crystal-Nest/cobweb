package it.crystalnest.cobweb.api.pack.generation;

import it.crystalnest.cobweb.api.pack.generation.input.CapturedInput;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Function;

/**
 * Configures a registered callback during mod initialization.
 * Each registration accepts one cache policy; subsequent configuration attempts fail.
 * Caching is opt-in for each Java callback. An opted-in callback must account for any output-affecting state that Cobweb cannot observe or fingerprint.
 * Cobweb reuses contributions from the most recent successfully applied result when its inputs still match. If it cannot verify reuse, it runs the callback.
 */
public final class CallbackRegistration {
  /**
   * The optional cache policy installed during mod initialization.
   */
  @Nullable
  private volatile Policy policy;

  CallbackRegistration() {}

  /**
   * Enables reuse within the current process based on observable inputs.
   *
   * @return this registration.
   * @throws IllegalStateException if callback declarations have closed or caching is already configured.
   */
  public CallbackRegistration cache() {
    return configure(null, null);
  }

  /**
   * Enables persistent caching with a stable key unique within this pack.
   *
   * @param key nonblank pack-local callback identity.
   * @return this registration.
   * @throws IllegalStateException if callback declarations have closed or caching is already configured.
   * @throws IllegalArgumentException if the key is blank.
   * @throws NullPointerException if the key is {@code null}.
   */
  public CallbackRegistration cache(String key) {
    return configure(Objects.requireNonNull(key), null);
  }

  /**
   * Enables reuse within the current process with an additional fingerprint derived from captured inputs.
   *
   * @param fingerprint returns a stable string, primitive wrapper or enum that represents additional dependencies. Other values cause the callback to run uncached.
   * @return this registration.
   * @throws IllegalStateException if callback declarations have closed or caching is already configured.
   * @throws NullPointerException if the fingerprint function is {@code null}.
   */
  public CallbackRegistration cache(Function<CapturedValues, ?> fingerprint) {
    return configure(null, Objects.requireNonNull(fingerprint));
  }

  /**
   * Enables persistent caching with a stable key and an additional fingerprint.
   *
   * @param key nonblank pack-local callback identity.
   * @param fingerprint returns a stable string, primitive wrapper or enum that represents additional dependencies. Other values cause the callback to run uncached.
   * @return this registration.
   * @throws IllegalStateException if callback declarations have closed or caching is already configured.
   * @throws IllegalArgumentException if the key is blank.
   * @throws NullPointerException if either argument is {@code null}.
   */
  public CallbackRegistration cache(String key, Function<CapturedValues, ?> fingerprint) {
    return configure(Objects.requireNonNull(key), Objects.requireNonNull(fingerprint));
  }

  /**
   * Installs the callback's one cache policy before declarations close.
   *
   * @param key stable pack-local key, or {@code null} for session reuse.
   * @param fingerprint additional captured-input fingerprint, or {@code null} to use automatic observations alone.
   * @return this registration.
   * @throws IllegalStateException if declarations have closed or caching is already configured.
   * @throws IllegalArgumentException if the key is blank.
   */
  private synchronized CallbackRegistration configure(@Nullable String key, @Nullable Function<CapturedValues, ?> fingerprint) {
    if (DynamicPackLoads.declarationsClosed()) {
      throw new IllegalStateException("Pack callback declarations have closed");
    }
    if (key != null && key.isBlank()) {
      throw new IllegalArgumentException("Cache key cannot be blank");
    }
    if (policy != null) {
      throw new IllegalStateException("Caching already configured for this callback");
    }
    policy = new Policy(key, fingerprint);
    return this;
  }

  /**
   * Returns the installed cache policy.
   *
   * @return cache policy, or {@code null} if caching was omitted.
   */
  @Nullable
  Policy policy() {
    return policy;
  }

  /**
   * Describes the optional persistent cache key and extra input fingerprint.
   *
   * @param key stable pack-local identity, or {@code null} for session reuse.
   * @param fingerprint additional fingerprint function, or {@code null} to use automatic observations alone.
   */
  record Policy(@Nullable String key, @Nullable Function<CapturedValues, ?> fingerprint) {}

  /**
   * Supplies the same captured values that the callback receives.
   */
  public interface CapturedValues {
    /**
     * Reads a declared load-start input.
     *
     * @param input captured input declaration.
     * @param <T> captured value type.
     * @return the value captured for this load.
     * @throws IllegalArgumentException if the input was not declared.
     */
    <T> T value(CapturedInput<T> input);
  }
}
