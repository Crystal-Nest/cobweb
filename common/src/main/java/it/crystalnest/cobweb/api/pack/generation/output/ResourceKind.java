package it.crystalnest.cobweb.api.pack.generation.output;

import com.mojang.serialization.Codec;
import it.crystalnest.cobweb.api.pack.generation.lifecycle.Phase;
import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * Defines the JSON path, codec and native loading contract for a custom resource family.
 * The directory must be a nonempty relative resource path without empty, {@code .} or {@code ..} components.
 *
 * @param directory directory within a resource namespace.
 * @param codec codec for complete definitions.
 * @param lifetime resource lifetime.
 * @param deadline last phase in which this family can be produced.
 * @param <T> definition type.
 */
public record ResourceKind<T>(String directory, Codec<T> codec, ResourceLifetime lifetime, Phase deadline) {
  /**
   * @param directory directory within a resource namespace.
   * @param codec codec for complete definitions.
   * @param lifetime lifetime of generated definitions.
   * @param deadline last compatible generation phase.
   */
  public ResourceKind {
    Objects.requireNonNull(codec);
    Objects.requireNonNull(lifetime);
    Objects.requireNonNull(deadline);
    if (directory.isEmpty() || !directory.chars().allMatch(value -> Identifier.validPathChar((char) value))) {
      throw new IllegalArgumentException("Invalid resource directory: " + directory);
    }
    for (String part : directory.split("/", -1)) {
      if (part.isEmpty() || part.equals(".") || part.equals("..")) {
        throw new IllegalArgumentException("Invalid resource directory: " + directory);
      }
    }
  }

  /**
   * Creates a reusable contract for a custom JSON resource family.
   *
   * @param directory directory within a resource namespace.
   * @param codec codec for complete definitions.
   * @param lifetime resource lifetime.
   * @param deadline last phase compatible with the custom reader.
   * @param <T> definition type.
   * @return custom resource contract.
   * @throws IllegalArgumentException if the resource directory is invalid.
   */
  public static <T> ResourceKind<T> json(String directory, Codec<T> codec, ResourceLifetime lifetime, Phase deadline) {
    return new ResourceKind<>(directory, codec, lifetime, deadline);
  }

  /**
   * Creates an output descriptor for phase inference.
   *
   * @return declaration for callbacks producing this resource family.
   */
  public Output output() {
    return new Output(deadline, lifetime, null, directory);
  }
}
