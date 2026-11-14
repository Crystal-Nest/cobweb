package it.crystalnest.cobweb.api.pack.generation;

import it.crystalnest.cobweb.api.pack.generation.input.CapturedInput;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Function;

/**
 * Configures a registered callback during mod initialization.
 */
public final class CallbackRegistration {
  @Nullable
  private volatile Policy policy;

  CallbackRegistration() {}

  /**
   * Enables reuse within the current process based on observable inputs.
   *
   * @return this registration.
   */
  public CallbackRegistration cache() {
    return configure(null, null);
  }

  /**
   * Enables caching with a stable key within this pack.
   *
   * @param key pack-local callback identity.
   * @return this registration.
   */
  public CallbackRegistration cache(String key) {
    return configure(Objects.requireNonNull(key), null);
  }

  /**
   * Enables caching with an additional fingerprint derived from captured inputs.
   *
   * @param fingerprint returns a stable string, primitive wrapper or enum describing additional dependencies; other values disable caching with a warning.
   * @return this registration.
   */
  public CallbackRegistration cache(Function<CapturedValues, ?> fingerprint) {
    return configure(null, Objects.requireNonNull(fingerprint));
  }

  /**
   * Enables caching with an explicit callback key and additional fingerprint.
   *
   * @param key pack-local callback identity.
   * @param fingerprint returns a stable string, primitive wrapper or enum describing additional dependencies; other values disable caching with a warning.
   * @return this registration.
   */
  public CallbackRegistration cache(String key, Function<CapturedValues, ?> fingerprint) {
    return configure(Objects.requireNonNull(key), Objects.requireNonNull(fingerprint));
  }

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

  @Nullable
  Policy policy() {
    return policy;
  }

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
     */
    <T> T value(CapturedInput<T> input);
  }
}
