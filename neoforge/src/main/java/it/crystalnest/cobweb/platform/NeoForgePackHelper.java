package it.crystalnest.cobweb.platform;

import it.crystalnest.cobweb.api.pack.ResourcePack;
import it.crystalnest.cobweb.api.pack.generation.DynamicPackLoads;
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
  private volatile IEventBus bus;

  @Override
  protected void addPackSource(ResourcePack pack, Supplier<Pack> supplier) {
    var initializedBus = bus;
    if (initializedBus == null) {
      throw new IllegalStateException("Cannot declare " + pack.type() + " pack " + pack.identifier() + " before Cobweb's NeoForge pack service is ready. Set ordering = \"AFTER\" on the required Cobweb dependency in neoforge.mods.toml.");
    }
    initializedBus.addListener((AddPackFindersEvent event) -> {
      if (event.getPackType() == pack.type()) {
        event.addRepositorySource(consumer -> {
          Pack nativePack = supplier.get();
          if (nativePack != null) {
            consumer.accept(nativePack);
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
  public BundledResources openBundledPack(PackLocationInfo location, String owner, String root) {
    return ModList.get().getModContainerById(owner).map(container -> {
      var contents = container.getModInfo().getOwningFile().getFile().getContents();
      return new BundledResources(new JarContentsPackResources(location, contents, root), (type, namespace, output) -> {
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
