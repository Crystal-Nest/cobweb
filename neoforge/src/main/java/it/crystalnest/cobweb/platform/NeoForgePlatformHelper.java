package it.crystalnest.cobweb.platform;

import it.crystalnest.cobweb.api.pack.dependency.VersionRange;
import it.crystalnest.cobweb.platform.model.Platform;
import it.crystalnest.cobweb.platform.services.PlatformHelper;
import net.minecraft.server.packs.repository.Pack;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
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
    return ModList.get().getMods().stream().map(mod -> mod.getModId()).sorted().toList();
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
    String lower = range.minimum() == null ? "(" : (range.minimum().inclusive() ? "[" : "(") + range.minimum().version();
    String upper = range.maximum() == null ? ")" : range.maximum().version() + (range.maximum().inclusive() ? "]" : ")");
    String spec = range.exact() == null ? lower + "," + upper : "[" + range.exact() + "]";
    try {
      return org.apache.maven.artifact.versioning.VersionRange.createFromVersionSpec(spec).containsVersion(actual);
    } catch (InvalidVersionSpecificationException failure) {
      throw new IllegalArgumentException("Invalid NeoForge version requirement for " + modId, failure);
    }
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
