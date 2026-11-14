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

  sealed interface Operation {}

  record Replacement(PackFile contents, boolean companion) implements Operation {}

  record Member(String name, PackFile contents) implements Operation {}

  record TagAddition(GenerationOutput.TagAddition tag) implements Operation {}

  record ClearMetadata() implements Operation {}

  record Submission(long sequence, Task task, Operation operation) {}

  static GeneratedPackResources.File merge(GeneratedPackResources pack, Identifier id, Collection<Submission> operations, Phase phase, Stage stage, StageResourceSnapshots snapshots) {
    GeneratedPackResources.File result = pack.file(id);
    Task lastReplacement = null;
    TagFile tag = null;
    JsonObject members = null;
    Map<String, Task> memberOwners = new HashMap<>();
    ResourceLifetime lifetime = ResourceLifetime.RELOADABLE;
    for (Submission submission : operations.stream().sorted(Comparator.comparingLong(Submission::sequence)).toList()) {
      if (submission.operation instanceof Member member) {
        if (members == null) {
          var base = readJson(pack, id, result, snapshots);
          members = base == null ? new JsonObject() : base.getAsJsonObject();
        }
        var previous = memberOwners.put(member.name(), submission.task);
        if (previous != null && previous != submission.task || lastReplacement != null && lastReplacement != submission.task && (!lastReplacement.fileRule() || !submission.task.fileRule())) {
          Constants.LOGGER.warn("Competing callbacks submitted {} in {} of pack {} at {}/{}", member.name(), id, pack.packId(), phase, stage);
        }
        members.add(member.name(), readJson(new GeneratedPackResources.File(member.contents(), submission.task.lifetime())));
        lifetime = submission.task.lifetime();
      } else if (submission.operation instanceof Replacement replacement) {
        if (lastReplacement != null && lastReplacement != submission.task && (!lastReplacement.fileRule() || !submission.task.fileRule())) {
          Constants.LOGGER.warn("Competing callbacks submitted {} in pack {} at {}/{}", id, pack.packId(), phase, stage);
        }
        if (memberOwners.values().stream().anyMatch(owner -> owner != submission.task)) {
          Constants.LOGGER.warn("A complete file superseded keyed contributions to {} in pack {} at {}/{}", id, pack.packId(), phase, stage);
        }
        lastReplacement = submission.task;
        result = new GeneratedPackResources.File(replacement.contents(), submission.task.lifetime(), replacement.companion());
        tag = null;
        members = null;
        memberOwners.clear();
      } else if (submission.operation instanceof ClearMetadata && result != null && result.companion()) {
        result = null;
      } else if (submission.operation instanceof TagAddition addition) {
        if (tag == null) {
          var base = readJson(pack, id, result, snapshots);
          tag = base == null ? new TagFile(List.of(), false) : TagFile.CODEC.parse(JsonOps.INSTANCE, base).getOrThrow();
        }
        Map<String, TagEntry> combined = new LinkedHashMap<>();
        tag.entries().forEach(entry -> combined.put(entry.toString(), entry));
        addition.tag().entries().forEach(entry -> combined.putIfAbsent(entry.toString(), entry));
        tag = new TagFile(List.copyOf(combined.values()), addition.tag().replace() == null ? tag.replace() : addition.tag().replace());
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

  private static JsonElement readJson(GeneratedPackResources.File file) {
    try (var reader = new InputStreamReader(file.content().open(), StandardCharsets.UTF_8)) {
      return JsonParser.parseReader(reader);
    } catch (IOException failure) {
      throw new UncheckedIOException("Unable to read this pack's JSON contribution", failure);
    }
  }
}
