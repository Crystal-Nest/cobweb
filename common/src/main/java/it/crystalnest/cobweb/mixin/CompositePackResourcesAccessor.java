package it.crystalnest.cobweb.mixin;

import net.minecraft.server.packs.CompositePackResources;
import net.minecraft.server.packs.PackResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Injects into {@link CompositePackResources} to insert generated content between the base and native overlays.
 */
@Mixin(CompositePackResources.class)
public interface CompositePackResourcesAccessor {
  /**
   * Returns the native resources from highest to lowest priority.
   *
   * @return overlays followed by the base.
   */
  @Accessor("packResourcesStack")
  List<PackResources> getPackResourcesStack();
}
