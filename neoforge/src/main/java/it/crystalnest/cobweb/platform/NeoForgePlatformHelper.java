package it.crystalnest.cobweb.platform;

import it.crystalnest.cobweb.api.pack.dependency.VersionRange;
import it.crystalnest.cobweb.api.pack.dependency.VersionRange.Bound.Inclusion;
import it.crystalnest.cobweb.platform.model.Platform;
import it.crystalnest.cobweb.platform.services.PlatformHelper;
import net.minecraft.server.packs.repository.Pack;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforgespi.language.IModInfo;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.Restriction;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

/**
 * NeoForge platform helper.
 */
public final class NeoForgePlatformHelper implements PlatformHelper {
  /**
   * Checks NeoForge's native hidden-pack state.
   *
   * @param pack native pack.
   * @return whether NeoForge hides the pack from the selection UI.
   */
  @Override
  public boolean isPackHidden(Pack pack) {
    return pack.isHidden();
  }

  @Override
  public Platform getPlatformName() {
    return Platform.NEOFORGE;
  }

  @Override
  public boolean isModLoaded(String modId) {
    return ModList.get().isLoaded(modId);
  }

  @Override
  public List<String> loadedModIds() {
    return ModList.get().getMods().stream().map(IModInfo::getModId).sorted().toList();
  }

  @Override
  public List<Path> modContentPaths() {
    return ModList.get().getMods().stream().flatMap(mod -> mod.getOwningFile().getFile().getContents().getContentRoots().stream()).distinct().toList();
  }

  @Override
  @Nullable
  public ModResource modResource(String modId, String path) {
    return ModList.get().getModContainerById(modId).flatMap(container -> {
      var contents = container.getModInfo().getOwningFile().getFile().getContents();
      return contents.findFile(path).map(uri -> new ModResource(uri, () -> contents.openFile(path)));
    }).orElse(null);
  }

  @Override
  public boolean matchesModVersion(String modId, VersionRange range) {
    var actual = ModList.get().getModContainerById(modId).orElseThrow().getModInfo().getVersion();
    return matchesVersion(actual, range);
  }

  @Override
  public boolean matchesVersion(String version, VersionRange range) {
    return matchesVersion(new DefaultArtifactVersion(version), range);
  }

  /**
   * Evaluates a constraint against a version already parsed by Maven.
   *
   * @param actual version to check.
   * @param range requested version constraint.
   * @return whether the version satisfies the constraint.
   * @throws IllegalArgumentException if the interval is empty.
   */
  private static boolean matchesVersion(ArtifactVersion actual, VersionRange range) {
    return switch (range) {
      case VersionRange.Exact(var version) -> new DefaultArtifactVersion(version).compareTo(actual) == 0;
      case VersionRange.Interval interval -> matchesInterval(actual, interval);
    };
  }

  /**
   * Evaluates interval endpoints using Maven's native version ordering.
   *
   * @param actual version to check.
   * @param interval interval with one or two endpoints.
   * @return whether the version is inside the interval.
   * @throws IllegalArgumentException if the interval is empty.
   */
  private static boolean matchesInterval(ArtifactVersion actual, VersionRange.Interval interval) {
    var minimum = interval.minimum();
    var maximum = interval.maximum();
    var lower = minimum == null ? null : new DefaultArtifactVersion(minimum.version());
    var upper = maximum == null ? null : new DefaultArtifactVersion(maximum.version());
    boolean includeLower = minimum != null && minimum.inclusion() == Inclusion.INCLUSIVE;
    boolean includeUpper = maximum != null && maximum.inclusion() == Inclusion.INCLUSIVE;
    boolean validInterval = true;
    if (lower != null && upper != null) {
      int order = lower.compareTo(upper);
      validInterval = order < 0 || order == 0 && includeLower && includeUpper;
    }
    if (validInterval) {
      return new Restriction(lower, includeLower, upper, includeUpper).containsVersion(actual);
    }
    throw new IllegalArgumentException("Empty NeoForge version interval: " + interval);
  }

  @Override
  public String getModVersion(String modId) {
    return ModList.get().getModContainerById(modId).orElseThrow().getModInfo().getVersion().toString();
  }

  @Override
  public Path getResourcePath(String modId, String path) {
    return Path.of(ModList.get().getModContainerById(modId).orElseThrow().getModInfo().getOwningFile().getFile().getContents().findFile(path + "/pack.mcmeta").orElseThrow()).getParent();
  }

  @Override
  public boolean isDevEnv() {
    return !FMLLoader.getCurrent().isProduction();
  }
}
