package it.crystalnest.cobweb.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import it.crystalnest.cobweb.Constants;
import net.minecraft.client.resources.SplashManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.stream.Stream;

/**
 * Injects into {@link SplashManager} to append Cobweb's main-menu splashes.
 */
@Mixin(SplashManager.class)
public abstract class SplashManagerMixin {
  /**
   * Resource containing Cobweb's additional splash lines.
   */
  @Unique
  private static final Identifier ADDITIONAL_SPLASHES = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "texts/splashes.txt");

  /**
   * Modifies the splash text stream returned by {@link BufferedReader#lines()} inside the method {@link SplashManager#prepare(ResourceManager, ProfilerFiller)}.<br>
   * Appends Cobweb's lines before native filtering and styling without replacing the selected Minecraft splash resource.
   *
   * @param original native splash text stream.
   * @param manager selected client resources.
   * @param profiler native reload profiler.
   * @return native splash text stream followed by Cobweb's lines, or the original stream if the additional resource is absent or unreadable.
   */
  @ModifyExpressionValue(method = "prepare(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)Ljava/util/List;", at = @At(value = "INVOKE", target = "Ljava/io/BufferedReader;lines()Ljava/util/stream/Stream;"))
  private Stream<String> modifyLines(Stream<String> original, ResourceManager manager, ProfilerFiller profiler) {
    var resource = manager.getResource(ADDITIONAL_SPLASHES);
    if (resource.isEmpty()) {
      return original;
    }
    try (var reader = resource.get().openAsReader()) {
      var additions = reader.lines()
        .filter(line -> !line.isBlank())
        .toList();
      return Stream.concat(original, additions.stream());
    } catch (IOException | UncheckedIOException failure) {
      Constants.LOGGER.error("Failed to read splash resource {}", ADDITIONAL_SPLASHES, failure);
      return original;
    }
  }
}
