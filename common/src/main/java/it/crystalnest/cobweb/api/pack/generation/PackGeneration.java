package it.crystalnest.cobweb.api.pack.generation;

import it.crystalnest.cobweb.api.pack.ResourcePack.AsyncCallback;
import it.crystalnest.cobweb.api.pack.generation.input.Input;
import it.crystalnest.cobweb.api.pack.generation.input.Inputs;
import it.crystalnest.cobweb.api.pack.generation.lifecycle.LoadKind;
import it.crystalnest.cobweb.api.pack.generation.lifecycle.Phase;
import it.crystalnest.cobweb.api.pack.generation.lifecycle.Stage;
import it.crystalnest.cobweb.api.pack.generation.output.Output;
import it.crystalnest.cobweb.api.pack.generation.output.Outputs;
import it.crystalnest.cobweb.api.pack.generation.output.ResourceLifetime;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Holds one registered pack's Java generation settings and callbacks.
 */
@ApiStatus.Internal
public final class PackGeneration {
  /**
   * Logical identifier used in declaration errors.
   */
  private final Identifier id;

  /**
   * Native pack type used for phase and output validation.
   */
  private final PackType type;

  /**
   * Immutable set of namespaces allowed for Java output.
   */
  final Set<String> namespaces;

  /**
   * Immutable set of preferred generation predecessors.
   */
  final Set<Identifier> after;

  /**
   * Immutable set of preferred generation successors.
   */
  final Set<Identifier> before;

  /**
   * Java generation tasks in declaration order.
   */
  private final List<Task> tasks = new ArrayList<>();

  /**
   * @param id logical pack identifier.
   * @param type native pack type.
   * @param namespaces namespaces allowed for Java output.
   * @param after logical IDs of preferred generation predecessors.
   * @param before logical IDs of preferred generation successors.
   */
  public PackGeneration(Identifier id, PackType type, Set<String> namespaces, Set<Identifier> after, Set<Identifier> before) {
    this.id = id;
    this.type = type;
    this.namespaces = Set.copyOf(namespaces);
    this.after = Set.copyOf(after);
    this.before = Set.copyOf(before);
  }

  /**
   * Schedules a Java callback for its compatible phases and stage.
   *
   * @param explicit requested phase, or {@code null} to infer the phase.
   * @param inputs input contracts.
   * @param outputs output contracts.
   * @param requestedStage requested stage, or {@code null} to infer the stage.
   * @param callback generation callback.
   * @return callback registration options.
   * @throws IllegalStateException if callback declarations have closed.
   * @throws IllegalArgumentException if the contracts are incompatible with the requested phase or stage, or no supported phase can be inferred.
   */
  public synchronized CallbackRegistration add(@Nullable Phase explicit, Inputs inputs, Outputs outputs, @Nullable Stage requestedStage, AsyncCallback callback) {
    if (DynamicPackLoads.declarationsClosed()) {
      throw new IllegalStateException("Pack callback declarations have closed: " + id);
    }
    Objects.requireNonNull(callback);
    boolean complete = inputs.values().stream().anyMatch(input -> input instanceof Input.Native nativeInput && nativeInput.completeSource());
    Stage stage = requestedStage == null ? complete ? Stage.TRANSFORMATION : Stage.GENERATION : requestedStage;
    if (complete && stage != Stage.TRANSFORMATION) {
      throw new IllegalArgumentException("Completed ordinary sources require the transformation stage");
    }
    if (explicit == null && outputs.values().isEmpty()) {
      throw new IllegalArgumentException("Generic output requires an explicit phase");
    }
    ResourceLifetime lifetime = outputs.rawLifetime();
    for (Output output : outputs.values()) {
      if (lifetime != null && lifetime != output.lifetime()) {
        throw new IllegalArgumentException("One callback cannot mix full-load and reloadable output");
      }
      lifetime = output.lifetime();
    }
    if (lifetime == null) {
      lifetime = explicit != null && explicit.fullLoadOnly() ? ResourceLifetime.FULL_LOAD : ResourceLifetime.RELOADABLE;
    }
    if (type == PackType.CLIENT_RESOURCES && lifetime == ResourceLifetime.FULL_LOAD) {
      throw new IllegalArgumentException("Client assets must be available on reloads");
    }
    Map<LoadKind, Phase> phases = new EnumMap<>(LoadKind.class);
    for (LoadKind kind : LoadKind.values()) {
      if (kind == LoadKind.RELOAD && lifetime == ResourceLifetime.FULL_LOAD) {
        continue;
      }
      Phase selected = null;
      for (Phase candidate : Phase.values()) {
        if ((explicit == null || candidate == explicit) && compatible(candidate, kind, inputs, outputs)) {
          selected = candidate;
          break;
        }
      }
      if (selected == null) {
        throw new IllegalArgumentException("No compatible " + kind + " phase for " + id + " with " + inputs + " and " + outputs);
      }
      phases.put(kind, selected);
    }
    var registration = new CallbackRegistration();
    tasks.add(new Task(Map.copyOf(phases), stage, lifetime, inputs, outputs, callback, registration, tasks.size(), TaskOrigin.JAVA, null));
    return registration;
  }

  /**
   * Checks whether a phase provides the required inputs and meets the output deadlines for this load.
   *
   * @param candidate phase to check.
   * @param kind kind of native load.
   * @param inputs input contracts.
   * @param outputs output contracts.
   * @return whether this phase can run the callback.
   */
  private boolean compatible(Phase candidate, LoadKind kind, Inputs inputs, Outputs outputs) {
    if (candidate.type() != type || kind == LoadKind.RELOAD && candidate.fullLoadOnly()) {
      return false;
    }
    for (Input input : inputs.values()) {
      if (input instanceof Input.Native(Phase readiness, Phase reloadReadiness, boolean completeSource)) {
        Phase ready = kind == LoadKind.FULL_LOAD ? readiness : reloadReadiness;
        if (ready.type() != type || candidate.ordinal() < ready.ordinal() || completeSource && candidate != ready) {
          return false;
        }
      }
    }
    return outputs.values().stream().allMatch(output -> output.deadline().type() == type && candidate.ordinal() <= output.deadline().ordinal());
  }

  /**
   * Returns the Java callbacks declared for this pack.
   *
   * @return snapshot of the declared tasks.
   */
  synchronized List<Task> tasks() {
    return List.copyOf(tasks);
  }

  /**
   * Indicates whether this pack has local Java generation callbacks.
   *
   * @return whether any Java callbacks are declared.
   */
  public synchronized boolean generates() {
    return !tasks.isEmpty();
  }

  /**
   * Identifies where a generation task was declared.
   */
  enum TaskOrigin {
    /**
     * Task registered by a Java callback.
     */
    JAVA,
    /**
     * Task compiled from a file rule.
     */
    FILE_RULE
  }

  /**
   * Describes a generation task and its scheduling contracts.
   *
   * @param phases phase for each supported load kind.
   * @param stage stage within the selected phase.
   * @param lifetime lifetime of the submitted files.
   * @param inputs input contracts.
   * @param outputs output contracts.
   * @param callback generation callback.
   * @param registration callback options.
   * @param index declaration index within the task origin.
   * @param origin Java or file-rule declaration.
   * @param fileRuleFingerprint fingerprint of a file rule, or {@code null} for a Java task.
   */
  record Task(Map<LoadKind, Phase> phases, Stage stage, ResourceLifetime lifetime, Inputs inputs, Outputs outputs, AsyncCallback callback, CallbackRegistration registration, int index, TaskOrigin origin,
    @Nullable String fileRuleFingerprint) {
    /**
     * Indicates whether this task came from a declarative file rule.
     *
     * @return whether the declaration is a file rule.
     */
    boolean fileRule() {
      return origin == TaskOrigin.FILE_RULE;
    }
  }
}
