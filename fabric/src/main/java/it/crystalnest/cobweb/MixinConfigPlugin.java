package it.crystalnest.cobweb;

import com.bawnorton.mixinsquared.MixinSquaredBootstrap;
import it.crystalnest.cobweb.platform.Services;
import org.jetbrains.annotations.ApiStatus;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Mixin Squared plugin configuration.
 */
@ApiStatus.Internal
public final class MixinConfigPlugin implements IMixinConfigPlugin {
  @Override
  public void onLoad(String mixinPackage) {
    MixinSquaredBootstrap.init();
  }

  @Override
  public String getRefMapperConfig() {
    return null;
  }

  @Override
  public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
    return Services.PLATFORM.isModLoaded("fabric-entity-events-v1") || !"crystalspider.fabricpolyfill.mixin.LivingEntityMixinSquared".equals(mixinClassName);
  }

  @Override
  public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

  @Override
  public List<String> getMixins() {
    return null;
  }

  @Override
  public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

  @Override
  public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}