package it.crystalnest.cobweb.platform;

import it.crystalnest.cobweb.api.pack.dependency.VersionRange;
import it.crystalnest.cobweb.platform.model.Platform;
import it.crystalnest.cobweb.platform.services.PlatformHelper;
import net.fabricmc.fabric.impl.resource.pack.FabricPack;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.version.VersionComparisonOperator;
import net.minecraft.server.packs.repository.Pack;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Fabric platform helper.
 */
public final class FabricPlatformHelper implements PlatformHelper {
  /**
   * Checks Fabric's hidden-pack state.
   *
   * @param pack native pack.
   * @return whether Fabric hides the pack from the selection UI.
   */
  @Override
  public boolean isPackHidden(Pack pack) {
    return ((FabricPack) pack).fabric$isHidden();
  }

  @Override
  public Platform getPlatformName() {
    return Platform.FABRIC;
  }

  @Override
  public boolean isModLoaded(String modId) {
    return FabricLoader.getInstance().isModLoaded(modId);
  }

  @Override
  public List<String> loadedModIds() {
    return FabricLoader.getInstance().getAllMods().stream().map(container -> container.getMetadata().getId()).sorted().toList();
  }

  @Override
  public List<Path> modContentPaths() {
    return FabricLoader.getInstance().getAllMods().stream().flatMap(container -> container.getRootPaths().stream()).distinct().toList();
  }

  @Override
  @Nullable
  public ModResource modResource(String modId, String path) {
    return FabricLoader.getInstance().getModContainer(modId).flatMap(container -> container.findPath(path))
      .map(file -> new ModResource(file.toUri(), () -> Files.newInputStream(file))).orElse(null);
  }

  @Override
  public boolean matchesModVersion(String modId, VersionRange range) {
    var actual = FabricLoader.getInstance().getModContainer(modId).orElseThrow().getMetadata().getVersion();
    return matchesVersion(actual, range);
  }

  @Override
  public boolean matchesVersion(String version, VersionRange range) {
    return matchesVersion(parseVersion(version), range);
  }

  /**
   * Evaluates a constraint against a version already parsed by Fabric.
   *
   * @param actual version to check.
   * @param range requested version constraint.
   * @return whether the version satisfies the constraint.
   */
  private static boolean matchesVersion(Version actual, VersionRange range) {
    if (range.exact() != null) {
      return VersionComparisonOperator.EQUAL.test(actual, parseVersion(range.exact()));
    }
    return (range.minimum() == null || matchesBound(actual, range.minimum(), true))
           && (range.maximum() == null || matchesBound(actual, range.maximum(), false));
  }

  /**
   * Parses a single version using Fabric's version parser.
   *
   * @param value version string.
   * @return parsed version.
   * @throws IllegalArgumentException if Fabric rejects the version.
   */
  private static Version parseVersion(String value) {
    try {
      return Version.parse(value);
    } catch (VersionParsingException failure) {
      throw new IllegalArgumentException("Invalid Fabric version: " + value, failure);
    }
  }

  /**
   * Tests a literal version boundary using Fabric's native comparison rules.
   * Inclusive bounds fall back to equality for opaque versions. Fabric requires a semantic version at an exclusive boundary.
   *
   * @param actual version to check.
   * @param bound requested version boundary.
   * @param lower whether this is the lower boundary.
   * @return whether the version satisfies the boundary.
   * @throws IllegalArgumentException if an exclusive boundary uses an opaque version.
   */
  private static boolean matchesBound(Version actual, VersionRange.Bound bound, boolean lower) {
    var endpoint = parseVersion(bound.version());
    if (!bound.inclusive() && !(endpoint instanceof SemanticVersion)) {
      throw new IllegalArgumentException("Fabric cannot use an opaque version as an exclusive bound: " + bound.version());
    }
    var operator = lower
      ? (bound.inclusive() ? VersionComparisonOperator.GREATER_EQUAL : VersionComparisonOperator.GREATER)
      : (bound.inclusive() ? VersionComparisonOperator.LESS_EQUAL : VersionComparisonOperator.LESS);
    return operator.test(actual, endpoint);
  }

  @Override
  public String getModVersion(String modId) {
    return FabricLoader.getInstance().getModContainer(modId).orElseThrow().getMetadata().getVersion().getFriendlyString();
  }

  @Override
  public Path getResourcePath(String modId, String path) {
    return FabricLoader.getInstance().getModContainer(modId).flatMap(container -> container.findPath(path)).orElseThrow();
  }

  @Override
  public boolean isDevEnv() {
    return FabricLoader.getInstance().isDevelopmentEnvironment();
  }
}
