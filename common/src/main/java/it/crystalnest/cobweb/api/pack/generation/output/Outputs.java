package it.crystalnest.cobweb.api.pack.generation.output;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Declared generation outputs and optional lifetime for custom files.
 *
 * @param values output contracts.
 * @param rawLifetime lifetime of raw files with an explicit phase.
 */
public record Outputs(List<Output> values, @Nullable ResourceLifetime rawLifetime) {
  /**
   * Determines available native outputs from an explicitly chosen phase.
   */
  public static final Outputs UNSPECIFIED = new Outputs(List.of(), null);

  /**
   * @param values native and custom output declarations.
   * @param rawLifetime optional lifetime of exact raw file submissions.
   */
  public Outputs {
    values = List.copyOf(values);
  }

  /**
   * Creates a declaration for the given output families.
   *
   * @param values output contracts.
   * @return immutable output declaration.
   */
  public static Outputs of(Output... values) {
    return new Outputs(List.of(values), null);
  }

  /**
   * Declares the lifetime of generic output for a callback with an explicit phase.
   *
   * @param lifetime native loading lifetime of the output.
   * @return declaration for generic file submissions.
   */
  public static Outputs raw(ResourceLifetime lifetime) {
    return new Outputs(List.of(), Objects.requireNonNull(lifetime));
  }
}
