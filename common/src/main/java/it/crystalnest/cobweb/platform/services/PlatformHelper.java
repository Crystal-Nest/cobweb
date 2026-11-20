package it.crystalnest.cobweb.platform.services;

import it.crystalnest.cobweb.api.pack.dependency.VersionRange;
import it.crystalnest.cobweb.platform.model.Environment;
import it.crystalnest.cobweb.platform.model.Platform;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Platform specific helper.
 */
public interface PlatformHelper {
  /**
   * Checks if the game is currently in a development environment.
   *
   * @return whether it's a development environment.
   */
  boolean isDevEnv();

  /**
   * Gets the name of the environment type as a string.
   *
   * @return name of the environment type.
   */
  default Environment getEnvironment() {
    return isDevEnv() ? Environment.DEVELOPMENT : Environment.PRODUCTION;
  }

  /**
   * Gets the name of the current platform
   *
   * @return name of the current platform.
   */
  Platform getPlatformName();

  /**
   * Checks if a mod with the given ID is loaded.
   *
   * @param modId mod ID.
   * @return whether the mod is loaded.
   */
  boolean isModLoaded(String modId);

  /**
   * Checks native pack visibility.
   *
   * @param pack native pack.
   * @return whether the loader hides this pack from the selection UI.
   */
  boolean isPackHidden(Pack pack);

  /**
   * Returns the IDs of loaded mods.
   *
   * @return installed mod IDs.
   */
  List<String> loadedModIds();

  /**
   * Returns the loaded artifacts or content roots used to validate persistent generated data.
   *
   * @return loader-owned source paths.
   */
  List<Path> modContentPaths();

  /**
   * Finds metadata physically stored at the root of a mod.
   *
   * @param modId loaded mod ID.
   * @return metadata source, if present.
   */
  @Nullable
  default ModResource modRootPackMetadata(String modId) {
    return modResource(modId, PackResources.PACK_META);
  }

  /**
   * Finds one file physically stored in the specified mod.
   *
   * @param modId loaded mod ID.
   * @param path relative path within the mod.
   * @return bundled file source, if present.
   */
  @Nullable
  ModResource modResource(String modId, String path);

  /**
   * Checks a dependency constraint using the loader's own mod version rules.
   *
   * @param modId loaded mod ID.
   * @param range requested version constraint.
   * @return whether the installed mod satisfies the constraint.
   * @throws IllegalArgumentException if the version constraint is invalid for the active loader.
   */
  boolean matchesModVersion(String modId, VersionRange range);

  /**
   * Checks a declared version using the same comparison rules as loaded mod versions.
   *
   * @param version declared version string.
   * @param range requested version constraint.
   * @return whether the declared version satisfies the constraint.
   * @throws IllegalArgumentException if the version or constraint is invalid for the active loader.
   */
  boolean matchesVersion(String version, VersionRange range);

  /**
   * Returns the version of the specified mod.
   *
   * @param modId mod ID.
   * @return mod version.
   * @throws NoSuchElementException if the mod is not loaded.
   */
  String getModVersion(String modId) throws NoSuchElementException;

  /**
   * Returns the {@link Path} for the resource of the specified mod under the given relative path.
   *
   * @param modId mod ID.
   * @param path relative path.
   * @return resource {@link Path}.
   * @throws NoSuchElementException if the mod is not loaded or the path can't be found.
   */
  Path getResourcePath(String modId, String path) throws NoSuchElementException;

  /**
   * Metadata in a loaded mod's actual content container.
   *
   * @param location physical resource identity.
   * @param stream supplier of a fresh stream for the metadata.
   */
  record ModResource(URI location, IoSupplier<InputStream> stream) {}
}
