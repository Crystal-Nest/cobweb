package it.crystalnest.cobweb.platform;

import it.crystalnest.cobweb.api.pack.dependency.VersionRange;
import it.crystalnest.cobweb.platform.model.Platform;
import it.crystalnest.cobweb.platform.services.PlatformHelper;
import net.fabricmc.fabric.impl.resource.pack.FabricPack;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
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
    try {
      if (range.exact() != null) {
        return VersionPredicate.parse("=" + range.exact()).test(actual);
      }
      return (range.minimum() == null || VersionPredicate.parse((range.minimum().inclusive() ? ">=" : ">") + range.minimum().version()).test(actual))
             && (range.maximum() == null || VersionPredicate.parse((range.maximum().inclusive() ? "<=" : "<") + range.maximum().version()).test(actual));
    } catch (VersionParsingException failure) {
      throw new IllegalArgumentException("Invalid Fabric version requirement for " + modId, failure);
    }
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
