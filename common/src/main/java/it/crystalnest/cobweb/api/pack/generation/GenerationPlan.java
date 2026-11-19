package it.crystalnest.cobweb.api.pack.generation;

import it.crystalnest.cobweb.api.pack.generation.PackGeneration.Task;
import it.crystalnest.cobweb.api.pack.generation.PackGeneration.TaskOrigin;
import it.crystalnest.cobweb.api.pack.generation.input.CapturedInput;
import it.crystalnest.cobweb.api.pack.generation.input.Inputs;
import it.crystalnest.cobweb.api.pack.generation.lifecycle.LoadKind;
import it.crystalnest.cobweb.api.pack.generation.lifecycle.Phase;
import it.crystalnest.cobweb.api.pack.generation.output.Output;
import it.crystalnest.cobweb.api.pack.generation.output.Outputs;
import it.crystalnest.cobweb.api.pack.generation.output.ResourceLifetime;
import it.crystalnest.cobweb.platform.Services;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Compiles the selected declarations, output contracts and dependency order for one native load.
 */
final class GenerationPlan {
  /**
   * File extension shared by JSON resource families.
   */
  private static final String JSON_EXTENSION = ".json";

  /**
   * Generation participants in topological order, each mapped to its transitive predecessors.
   */
  final Map<GeneratedPackResources, List<GeneratedPackResources>> order;

  /**
   * Compiled file rules by pack in manifest order.
   */
  final Map<GeneratedPackResources, List<Task>> fileTasks;

  /**
   * Values captured once for each declared input object during this load.
   */
  final Map<CapturedInput<?>, Object> captured;

  /**
   * Resolved cache eligibility for each task in this load.
   */
  final Map<Task, TaskCachePolicy> caching;

  /**
   * Known native resource directories and their output contracts.
   */
  private final List<FileRule> fileRules = new ArrayList<>();

  /**
   * Native resource type being loaded.
   */
  private final PackType type;

  /**
   * Whether this plan is for a full load or reload.
   */
  private final LoadKind kind;

  /**
   * @param type native pack type.
   * @param generated selected generation openings.
   * @param required validated required predecessor IDs by native pack ID.
   * @param kind full load or reload.
   * @param cacheRoot physical cache scope, or {@code null} when unavailable.
   */
  GenerationPlan(PackType type, List<GeneratedPackResources> generated, Map<String, List<String>> required, LoadKind kind, @Nullable Path cacheRoot) {
    this.type = type;
    this.kind = kind;
    if (type == PackType.SERVER_DATA) {
      Services.REGISTRY.worldRegistries().forEach(entry -> addRegistryRules(entry.key(), Phase.STATIC_TAGS_READY, ResourceLifetime.FULL_LOAD));
      RegistryDataLoader.DIMENSION_REGISTRIES.forEach(entry -> addRegistryRules(entry.key(), Phase.WORLD_REGISTRIES_READY, ResourceLifetime.FULL_LOAD));
      Services.REGISTRY.reloadableRegistries().forEach(entry -> addRegistryRules(entry.key(), Phase.WORLD_CONTEXT_READY, ResourceLifetime.RELOADABLE));
      BuiltInRegistries.REGISTRY.forEach(registry -> fileRules.add(new FileRule(Registries.tagsDirPath(registry.key()), JSON_EXTENSION, Output.TAGS)));
      fileRules.add(new FileRule("function", ".mcfunction", new Output(Phase.RELOADABLE_REGISTRIES_READY, ResourceLifetime.RELOADABLE, null)));
      fileRules.add(new FileRule(PackPaths.STRUCTURE_DIRECTORY, PackPaths.STRUCTURE_EXTENSION, Output.STRUCTURES));
    }
    Map<Identifier, GeneratedPackResources> selected = new HashMap<>();
    Map<String, GeneratedPackResources> nativeIds = new HashMap<>();
    for (GeneratedPackResources pack : generated) {
      if (pack.logicalId != null && selected.putIfAbsent(pack.logicalId, pack) != null) {
        throw new IllegalStateException("Ambiguous generated pack ID " + pack.logicalId);
      }
      nativeIds.put(pack.packId(), pack);
    }
    Map<GeneratedPackResources, Set<GeneratedPackResources>> edges = new LinkedHashMap<>();
    for (GeneratedPackResources pack : generated) {
      edges.put(pack, new LinkedHashSet<>());
    }
    for (GeneratedPackResources pack : generated) {
      for (String id : required.getOrDefault(pack.packId(), List.of())) {
        addPredecessor(edges, pack, nativeIds.get(id));
      }
      var after = new LinkedHashSet<>(pack.rules.after());
      var before = new LinkedHashSet<>(pack.rules.before());
      if (pack.definition != null) {
        var generation = pack.definition.generation();
        after.addAll(generation.after);
        before.addAll(generation.before);
      }
      for (Identifier id : after) {
        addPredecessor(edges, pack, selected.get(id));
      }
      for (Identifier id : before) {
        addPredecessor(edges, selected.get(id), pack);
      }
    }
    Set<GeneratedPackResources> path = new LinkedHashSet<>();
    Map<GeneratedPackResources, List<GeneratedPackResources>> order = new LinkedHashMap<>();
    Map<CapturedInput<?>, Object> captured = new IdentityHashMap<>();
    Map<GeneratedPackResources, List<Task>> fileTasks = new IdentityHashMap<>();
    for (GeneratedPackResources pack : generated) {
      visit(pack, edges, path, order);
      Set<String> cacheKeys = new HashSet<>();
      fileTasks.put(pack, compileRules(pack));
      for (Task task : pack.javaTasks()) {
        var policy = task.registration().policy();
        if (policy != null && policy.key() != null && !cacheKeys.add(policy.key())) {
          throw new IllegalStateException("Duplicate callback cache key in " + pack.packId() + ": " + policy.key());
        }
        if (task.phases().containsKey(kind)) {
          task.inputs().values().stream().filter(CapturedInput.class::isInstance).map(CapturedInput.class::cast).forEach(input -> captured.computeIfAbsent(input, CapturedInput::capture));
        }
      }
    }
    Map<Task, TaskCachePolicy> caching = new IdentityHashMap<>();
    for (GeneratedPackResources pack : generated) {
      Stream.concat(pack.javaTasks().stream(), fileTasks.get(pack).stream()).forEach(task -> caching.put(task, TaskCachePolicy.resolve(task, cacheRoot)));
    }
    this.order = Collections.unmodifiableMap(order);
    this.fileTasks = Collections.unmodifiableMap(fileTasks);
    this.captured = Collections.unmodifiableMap(captured);
    this.caching = Collections.unmodifiableMap(caching);
  }

  /**
   * Compiles an owned file manifest using the known native output deadlines.
   *
   * @param pack pack opening that owns the manifest.
   * @return tasks for this load in manifest order.
   */
  private List<Task> compileRules(GeneratedPackResources pack) {
    var tasks = new ArrayList<Task>();
    for (var rule : pack.rules.rules()) {
      try {
        Output destination = type == PackType.CLIENT_RESOURCES ? Output.ASSETS : fileRules.stream().filter(entry -> entry.matches(rule.to())).map(FileRule::output)
          .min(Comparator.comparing(Output::deadline)).orElseThrow(() -> new IllegalArgumentException("Unknown server data destination " + rule.to()));
        Phase phase = rule.phase() == null ? destination.deadline() : rule.phase();
        if (phase.type() != type || phase.ordinal() > destination.deadline().ordinal() || phase.fullLoadOnly() && destination.lifetime() == ResourceLifetime.RELOADABLE) {
          throw new IllegalArgumentException(rule.to() + " cannot be generated in " + phase + " on " + kind);
        }
        if (kind != LoadKind.RELOAD || destination.lifetime() != ResourceLifetime.FULL_LOAD) {
          tasks.add(new Task(Map.of(kind, phase), rule.stage(), destination.lifetime(), Inputs.NONE, Outputs.UNSPECIFIED, (context, output) -> {
            try {
              rule.generate(context, output);
              return CompletableFuture.completedFuture(null);
            } catch (IOException | RuntimeException failure) {
              var route = rule.from() == null ? rule.to().toString() : rule.from() + " -> " + rule.to();
              throw new IllegalArgumentException(FilePackRules.PATH + " rule " + rule.index() + " (" + route + "): " + failure.getMessage(), failure);
            }
          }, new CallbackRegistration(), rule.index(), TaskOrigin.FILE_RULE, rule.fingerprint()));
        }
      } catch (RuntimeException failure) {
        throw new IllegalArgumentException(pack.packId() + "/" + FilePackRules.PATH + " rule " + rule.index() + ": " + failure.getMessage(), failure);
      }
    }
    return List.copyOf(tasks);
  }

  /**
   * Records an ordering edge when both packs participate in generation.
   *
   * @param edges predecessor sets by pack.
   * @param pack pack that should wait, or {@code null} if absent.
   * @param predecessor pack that must run first, or {@code null} if absent.
   */
  private static void addPredecessor(Map<GeneratedPackResources, Set<GeneratedPackResources>> edges, @Nullable GeneratedPackResources pack, @Nullable GeneratedPackResources predecessor) {
    if (pack != null && predecessor != null) {
      edges.get(pack).add(predecessor);
    }
  }

  /**
   * Adds native contracts for registry entries and tags.
   *
   * @param key registry key.
   * @param deadline last phase that can submit registry entries.
   * @param lifetime native lifetime for registry entries.
   */
  private void addRegistryRules(ResourceKey<? extends Registry<?>> key, Phase deadline, ResourceLifetime lifetime) {
    fileRules.add(new FileRule(Registries.elementsDirPath(key), JSON_EXTENSION, new Output(deadline, lifetime, key)));
    Phase tagDeadline = lifetime == ResourceLifetime.FULL_LOAD && kind == LoadKind.RELOAD ? Phase.DATA_RESOURCES : deadline;
    fileRules.add(new FileRule(Registries.tagsDirPath(key), JSON_EXTENSION, new Output(tagDeadline, ResourceLifetime.RELOADABLE, null)));
  }

  /**
   * Checks that a generated data path meets its native deadline, lifetime and declaration.
   *
   * @param task task submitting the file.
   * @param phase phase submitting the file.
   * @param id destination resource identifier.
   * @throws IllegalArgumentException if the output is undeclared or misses its native contract.
   */
  void checkOutput(Task task, Phase phase, Identifier id) {
    if (type == PackType.SERVER_DATA) {
      List<Output> rules = fileRules.stream().filter(rule -> rule.matches(id)).map(FileRule::output).toList();
      boolean declared = task.outputs().values().stream().anyMatch(output -> output.directory() != null && id.getPath().startsWith(output.directory() + "/") && id.getPath().endsWith(JSON_EXTENSION));
      if (rules.isEmpty() && !declared && task.outputs().rawLifetime() == null && !phase.fullLoadOnly()) {
        throw new IllegalArgumentException("Custom data output needs an explicit lifetime: " + id);
      }
      for (Output rule : rules) {
        if (phase.ordinal() > rule.deadline().ordinal() || task.lifetime() != rule.lifetime()) {
          throw new IllegalArgumentException(id + " must be generated by " + rule.deadline() + " with lifetime " + rule.lifetime());
        }
        if (rule.registry() != null && !task.outputs().values().isEmpty() && task.outputs().values().stream().noneMatch(output -> rule.registry().equals(output.registry()))) {
          throw new IllegalArgumentException("Callback did not declare registry output " + rule.registry().identifier());
        }
      }
    }
  }

  /**
   * Visits a pack's predecessors and records their transitive order, detecting dependency cycles.
   *
   * @param pack pack to schedule.
   * @param edges direct generation predecessors by pack.
   * @param path current traversal path.
   * @param order completed topological order.
   * @throws IllegalStateException if a generation ordering cycle is encountered.
   */
  private static void visit(GeneratedPackResources pack, Map<GeneratedPackResources, Set<GeneratedPackResources>> edges, Set<GeneratedPackResources> path, Map<GeneratedPackResources, List<GeneratedPackResources>> order) {
    if (!order.containsKey(pack)) {
      if (!path.add(pack)) {
        throw new IllegalStateException("Cyclic pack generation order: " + path.stream().map(PackResources::packId).toList() + " -> " + pack.packId());
      }
      Set<GeneratedPackResources> predecessors = new LinkedHashSet<>();
      for (GeneratedPackResources predecessor : edges.get(pack)) {
        visit(predecessor, edges, path, order);
        predecessors.addAll(order.get(predecessor));
        predecessors.add(predecessor);
      }
      order.put(pack, List.copyOf(predecessors));
      path.remove(pack);
    }
  }

  /**
   * Associates a native file family with its scheduling contract.
   *
   * @param directory path directory within a resource namespace.
   * @param extension required file extension.
   * @param output native output contract.
   */
  private record FileRule(String directory, String extension, Output output) {
    /**
     * Checks whether a namespaced file belongs to this family.
     *
     * @param id destination file identifier.
     * @return whether the path matches the directory and extension.
     */
    boolean matches(Identifier id) {
      return id.getPath().startsWith(directory + "/") && id.getPath().endsWith(extension);
    }
  }
}
