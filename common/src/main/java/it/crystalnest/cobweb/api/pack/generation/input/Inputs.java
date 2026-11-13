package it.crystalnest.cobweb.api.pack.generation.input;

import java.util.List;

/**
 * Declared generation inputs, including optional load-start captures.
 *
 * @param values immutable input declarations.
 */
public record Inputs(List<Input> values) {
  /**
   * No special input requirements.
   */
  public static final Inputs NONE = new Inputs(List.of());

  /**
   * Copies the callback's input declarations.
   *
   * @param values native and captured input declarations.
   */
  public Inputs {
    values = List.copyOf(values);
  }

  /**
   * Creates a declaration for the supplied inputs.
   *
   * @param values native capabilities and captured values.
   * @return immutable input declaration.
   */
  public static Inputs of(Input... values) {
    return new Inputs(List.of(values));
  }
}
