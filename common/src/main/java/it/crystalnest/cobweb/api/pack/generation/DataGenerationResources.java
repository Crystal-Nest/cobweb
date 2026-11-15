package it.crystalnest.cobweb.api.pack.generation;

import it.crystalnest.cobweb.api.pack.generation.lifecycle.Phase;
import it.crystalnest.cobweb.api.pack.generation.lifecycle.Stage;
import it.crystalnest.cobweb.api.registry.CobwebDataPackRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.crafting.Recipe;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Reads native data inputs from one server data generation callback.
 * Obtain this view from {@link GenerationContext#data()}. Registry and tag access depends on the inputs ready in the current {@link Phase}.
 */
public final class DataGenerationResources {
  /**
   * The server data callback that owns this resource view.
   */
  private final GenerationContext context;

  /**
   * @param context active server data callback context.
   */
  DataGenerationResources(GenerationContext context) {
    this.context = context;
  }

  /**
   * Returns the enabled feature flags for this native data load.
   *
   * @return selected native feature flags.
   */
  public FeatureFlagSet features() {
    return context.features();
  }

  /**
   * Looks up a registry definition using the context available in this phase.
   *
   * @param definition declared registry.
   * @param <T> entry type.
   * @return read-only registry lookup.
   * @throws IllegalStateException if the callback has ended or the registry is unavailable in this phase.
   */
  public <T> HolderLookup.RegistryLookup<T> registry(CobwebDataPackRegistry<T> definition) {
    return registry(definition.key());
  }

  /**
   * Looks up a native registry using the context available in this phase.
   *
   * @param key native registry key.
   * @param <T> entry type.
   * @return read-only registry lookup.
   * @throws IllegalStateException if the callback has ended or the registry is unavailable in this phase.
   */
  public <T> HolderLookup.RegistryLookup<T> registry(ResourceKey<? extends Registry<T>> key) {
    return context.registry(key);
  }

  /**
   * Reads the native tag reference exposed by the registry lookup for this phase.
   *
   * @param tag native tag key.
   * @param <T> entry type.
   * @return native reference, if present.
   * @throws IllegalStateException if the callback has ended or the tag's registry is unavailable in this phase.
   */
  public <T> Optional<HolderSet.Named<T>> tag(TagKey<T> tag) {
    return registry(tag.registry()).get(tag);
  }

  /**
   * Reads the membership staged by native tag preparation for the current load, even before Minecraft applies the pending tags.
   *
   * @param tag native tag key.
   * @param <T> entry type.
   * @return prepared membership, or an empty list for an absent tag.
   * @throws IllegalStateException if the callback has ended or native tag preparation has yet to finish.
   */
  public <T> List<Holder<T>> preparedTag(TagKey<T> tag) {
    return context.preparedTag(tag);
  }

  /**
   * Decodes the winning recipe file after ordinary recipe generation and before native decoding.
   *
   * @param recipe recipe identifier.
   * @return detached recipe definition if present.
   * @throws IllegalStateException if the completed recipe-source window is unavailable.
   * @throws IOException if the recipe cannot be read.
   */
  public Optional<Recipe<?>> completedRecipe(Identifier recipe) throws IOException {
    context.checkOpen();
    if (context.phase() != Phase.WORLD_CONTEXT_READY || context.stage() != Stage.TRANSFORMATION) {
      throw new IllegalStateException("Completed recipe sources require WORLD_CONTEXT_READY transformation");
    }
    var file = context.resources().get(FileToIdConverter.registry(Registries.RECIPE).idToFile(recipe));
    return file.isEmpty() ? Optional.empty() : Optional.of(file.get().decode(Recipe.DIRECT_CODEC));
  }

  /**
   * Reads a detached native structure with a caller-supplied decoding budget.
   *
   * @param id structure identifier.
   * @param budget native NBT accounting budget.
   * @return decoded structure if present.
   * @throws IOException if the structure cannot be read.
   */
  public Optional<CompoundTag> structure(Identifier id, NbtAccounter budget) throws IOException {
    var file = context.resources().get(id.withPath(path -> "structure/" + path + ".nbt"));
    if (file.isEmpty()) {
      return Optional.empty();
    }
    try (var input = file.get().open()) {
      return Optional.of(NbtIo.readCompressed(input, budget));
    }
  }
}
