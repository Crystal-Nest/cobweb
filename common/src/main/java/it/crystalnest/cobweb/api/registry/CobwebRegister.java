package it.crystalnest.cobweb.api.registry;

import java.util.function.Supplier;

/**
 * Loader agnostic register.
 *
 * @param <R> register type.
 */
public interface CobwebRegister<R> {
  /**
   * Registers the game object returned by the given supplier.
   *
   * @param name game object name.
   * @param supplier game object supplier.
   * @param <T> game object type.
   * @return registered game object holder.
   */
  <T extends R> CobwebEntry<T> register(String name, Supplier<? extends T> supplier);
}
