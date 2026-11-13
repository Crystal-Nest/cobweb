package it.crystalnest.cobweb.platform;

import it.crystalnest.cobweb.api.pack.generation.DynamicPackLoads;
import it.crystalnest.cobweb.impl.pack.BundledPackResources;
import it.crystalnest.cobweb.platform.services.PackHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.resource.JarContentsPackResources;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Adds Cobweb packs to NeoForge repositories and opens packs bundled with NeoForge mods.
 */
public final class NeoForgePackHelper extends PackHelper {
  /**
   * NeoForge event bus used for pack discovery.
   */
  private IEventBus bus;

  @Override
  protected void addPackSource(PackType type, Supplier<Pack> supplier) {
    bus.addListener((AddPackFindersEvent event) -> {
      if (event.getPackType() == type) {
        event.addRepositorySource(consumer -> {
          Pack pack = supplier.get();
          if (pack != null) {
            consumer.accept(pack);
          }
        });
      }
    });
  }

  @Override
  public String bundledKnownPackNamespace() {
    return "neoforge";
  }

  @Override
  @Nullable
  public BundledPackResources openBundledPack(PackLocationInfo location, String owner, String root) {
    return ModList.get().getModContainerById(owner).map(container -> {
      var contents = container.getModInfo().getOwningFile().getFile().getContents();
      return new BundledPackResources(new JarContentsPackResources(location, contents, root), (type, namespace, output) -> {
        var prefix = root + "/" + type.getDirectory() + "/" + namespace + "/";
        contents.visitContent(prefix, (path, resource) -> {
          var id = Identifier.tryBuild(namespace, path.substring(prefix.length()));
          if (id != null) {
            output.accept(id, resource.retain()::open);
          }
        });
      });
    }).orElse(null);
  }

  /**
   * Stores the event bus for pack discovery and closes pack declarations when mod loading completes.
   *
   * @param bus Cobweb mod event bus.
   */
  public void register(IEventBus bus) {
    this.bus = bus;
    bus.addListener((FMLLoadCompleteEvent event) -> DynamicPackLoads.closeDeclarations());
  }
}
