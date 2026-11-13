package it.crystalnest.cobweb.api.pack.generation.input;

import it.crystalnest.cobweb.api.pack.generation.lifecycle.Phase;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Identifies an immutable or detached value captured once at the start of a resource load.
 *
 * @param <T> captured value type.
 */
public final class CapturedInput<T> implements Input {
  private final Supplier<? extends T> supplier;

  private CapturedInput(Supplier<? extends T> supplier) {
    this.supplier = Objects.requireNonNull(supplier);
  }

  /**
   * Declares a load-start value. Reuse this declaration to share the captured value across callbacks.
   *
   * @param supplier supplies a non-null immutable value or detached snapshot.
   * @param <T> captured value type.
   * @return typed input declaration.
   */
  public static <T> CapturedInput<T> of(Supplier<? extends T> supplier) {
    return new CapturedInput<>(supplier);
  }

  @Override
  public Phase readiness() {
    return Phase.DATA_RESOURCES;
  }

  /**
   * Captures the value for the current load.
   *
   * @return non-null snapshot.
   */
  @org.jetbrains.annotations.ApiStatus.Internal
  public T capture() {
    return Objects.requireNonNull(supplier.get(), "Captured input returned null");
  }
}
