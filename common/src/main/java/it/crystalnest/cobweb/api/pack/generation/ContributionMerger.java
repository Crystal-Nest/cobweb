package it.crystalnest.cobweb.api.pack.generation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import it.crystalnest.cobweb.Constants;
import it.crystalnest.cobweb.api.pack.generation.PackGeneration.Task;
import it.crystalnest.cobweb.api.pack.generation.lifecycle.Phase;
import it.crystalnest.cobweb.api.pack.generation.lifecycle.Stage;
import it.crystalnest.cobweb.api.pack.generation.output.ResourceLifetime;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagFile;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies ordered generated operations while preserving native replacement and grouped-sidecar semantics.
 */
final class ContributionMerger {
  private ContributionMerger() {}

  /**
   * Describes one merge operation for a destination file.
   */
  sealed interface Operation {}

  /**
   * Replaces the complete file with owned contents.
   *
   * @param contents complete file backing.
   * @param companion whether the file is owned as a grouped sidecar.
   */
  record Replacement(PackFile contents, boolean companion) implements Operation {}

  /**
   * Replaces one named member of an aggregate file.
   *
   * @param name member name.
   * @param contents backing of the complete member value.
   */
  record Member(String name, PackFile contents) implements Operation {}

  /**
   * Appends native tag entries to this pack's existing tag file.
   *
   * @param tag tag additions and optional native replace setting.
   */
  record TagAddition(GenerationOutput.TagAddition tag) implements Operation {}

  /**
   * Clears a previously grouped sidecar for the destination.
   */
  record ClearMetadata() implements Operation {}

  /**
   * Records one operation submitted to this file in the current stage.
   *
   * @param sequence attempt-wide submission index.
   * @param task task that submitted the operation.
   * @param operation operation to merge.
   */
  record Submission(long sequence, Task task, Operation operation) {}

  /**
   * Applies same-pack contributions in submission order and reports competing writes.
   *
   * @param pack owning pack opening.
   * @param id destination file identifier.
   * @param operations submitted operations for the file.
   * @param phase active generation phase.
   * @param stage active generation stage.
   * @param snapshots stage snapshots used for bundled aggregate bases.
   * @return final generated file, or {@code null} when a grouped sidecar was cleared.
   */
  static GeneratedPackResources.File merge(GeneratedPackResources pack, Identifier id, Collection<Submission> operations, Phase phase, Stage stage, StageResourceSnapshots snapshots) {
    GeneratedPackResources.File result = pack.file(id);
    Task lastReplacement = null;
    TagFile tag = null;
    JsonObject members = null;
    Map<String, Task> memberOwners = new HashMap<>();
    ResourceLifetime lifetime = ResourceLifetime.RELOADABLE;
    for (Submission submission : operations.stream().sorted(Comparator.comparingLong(Submission::sequence)).toList()) {
      if (submission.operation instanceof Member(String name, PackFile contents)) {
        if (members == null) {
          var base = readJson(pack, id, result, snapshots);
          members = base == null ? new JsonObject() : base.getAsJsonObject();
        }
        var previous = memberOwners.put(name, submission.task);
        if (previous != null && previous != submission.task || competes(lastReplacement, submission.task)) {
          Constants.LOGGER.warn("Competing callbacks submitted {} in {} of pack {} at {}/{}", name, id, pack.packId(), phase, stage);
        }
        members.add(name, readJson(new GeneratedPackResources.File(contents, submission.task.lifetime())));
        lifetime = submission.task.lifetime();
      } else if (submission.operation instanceof Replacement(PackFile contents, boolean companion)) {
        if (competes(lastReplacement, submission.task)) {
          Constants.LOGGER.warn("Competing callbacks submitted {} in pack {} at {}/{}", id, pack.packId(), phase, stage);
        }
        if (memberOwners.values().stream().anyMatch(owner -> owner != submission.task)) {
          Constants.LOGGER.warn("A complete file superseded keyed contributions to {} in pack {} at {}/{}", id, pack.packId(), phase, stage);
        }
        lastReplacement = submission.task;
        result = new GeneratedPackResources.File(contents, submission.task.lifetime(), companion);
        tag = null;
        members = null;
        memberOwners.clear();
      } else if (submission.operation instanceof ClearMetadata && result != null && result.companion()) {
        result = null;
      } else if (submission.operation instanceof TagAddition(GenerationOutput.TagAddition addition)) {
        tag = appendTag(pack, id, result, snapshots, tag, addition);
      }
    }
    if (members != null) {
      return aggregate(pack, id, members.toString(), lifetime);
    }
    if (tag != null) {
      return aggregate(pack, id, TagFile.CODEC.encodeStart(JsonOps.INSTANCE, tag).getOrThrow().toString(), ResourceLifetime.RELOADABLE);
    }
    return result;
  }

  /**
   * Checks whether two distinct callbacks compete for a complete file in the same stage.
   * Ordered file-rule replacements do not produce a competition warning.
   *
   * @param previous earlier complete-file writer, or {@code null} when absent.
   * @param current current writer.
   * @return whether the overlapping writes require a warning.
   */
  private static boolean competes(@Nullable Task previous, Task current) {
    return previous != null && previous != current && (!previous.fileRule() || !current.fileRule());
  }

  /**
   * Appends unique tag entries to the accumulated tag or this pack's initial tag file.
   * Preserves the accumulated native replace flag unless the addition explicitly supplies one.
   *
   * @param pack owning pack opening.
   * @param id destination tag file.
   * @param file current generated file, or {@code null} to consult the bundled base.
   * @param snapshots stage snapshots for bundled reads.
   * @param tag accumulated tag, or {@code null} before the first addition.
   * @param addition submitted entries and optional replace flag.
   * @return combined tag definition.
   */
  private static TagFile appendTag(GeneratedPackResources pack, Identifier id, @Nullable GeneratedPackResources.File file, StageResourceSnapshots snapshots, @Nullable TagFile tag, GenerationOutput.TagAddition addition) {
    if (tag == null) {
      var base = readJson(pack, id, file, snapshots);
      tag = base == null ? new TagFile(List.of(), false) : TagFile.CODEC.parse(JsonOps.INSTANCE, base).getOrThrow();
    }
    Map<String, TagEntry> combined = new LinkedHashMap<>();
    tag.entries().forEach(entry -> combined.put(entry.toString(), entry));
    addition.entries().forEach(entry -> combined.putIfAbsent(entry.toString(), entry));
    return new TagFile(List.copyOf(combined.values()), addition.replace() == null ? tag.replace() : addition.replace());
  }

  /**
   * Writes a merged JSON file and transfers its backing to the owning pack.
   *
   * @param pack owning pack opening.
   * @param id destination file identifier.
   * @param json serialized JSON object.
   * @param lifetime native lifetime of the generated file.
   * @return complete generated file.
   */
  private static GeneratedPackResources.File aggregate(GeneratedPackResources pack, Identifier id, String json, ResourceLifetime lifetime) {
    try {
      var file = PackFile.write(stream -> stream.write(json.getBytes(StandardCharsets.UTF_8)));
      try {
        pack.adopt(file);
      } catch (RuntimeException | Error failure) {
        file.release();
        throw failure;
      }
      return new GeneratedPackResources.File(file, lifetime);
    } catch (IOException failure) {
      throw new UncheckedIOException("Unable to aggregate " + id + " in " + pack.packId(), failure);
    }
  }

  /**
   * Reads this pack's existing aggregate or its bundled base file.
   *
   * @param pack owning pack opening.
   * @param id destination file identifier.
   * @param file existing generated file, or {@code null} to consult the bundled base.
   * @param snapshots stage snapshots used to read the base file.
   * @return parsed JSON, or {@code null} if this pack has no base for the aggregate.
   */
  @Nullable
  private static JsonElement readJson(GeneratedPackResources pack, Identifier id, @Nullable GeneratedPackResources.File file, StageResourceSnapshots snapshots) {
    if (file != null) {
      return readJson(file);
    }
    var bundled = pack.bundledFile(id, snapshots);
    if (bundled != null) {
      try (var reader = new InputStreamReader(bundled.get(), StandardCharsets.UTF_8)) {
        return JsonParser.parseReader(reader);
      } catch (IOException failure) {
        throw new UncheckedIOException("Unable to read this pack's bundled JSON contribution " + id, failure);
      }
    }
    return null;
  }

  /**
   * Parses a generated file as JSON.
   *
   * @param file generated file to parse.
   * @return parsed JSON document.
   */
  private static JsonElement readJson(GeneratedPackResources.File file) {
    try (var reader = new InputStreamReader(file.content().open(), StandardCharsets.UTF_8)) {
      return JsonParser.parseReader(reader);
    } catch (IOException failure) {
      throw new UncheckedIOException("Unable to read this pack's JSON contribution", failure);
    }
  }
}
