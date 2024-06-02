package it.crystalnest.cobweb.platform;

import it.crystalnest.cobweb.api.item.TierUtils;
import it.crystalnest.cobweb.platform.services.ToolTiersHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Tier;
import net.minecraftforge.common.TierSortingRegistry;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Forge tool tiers helper.
 */
public final class ForgeToolTiersHelper implements ToolTiersHelper {
  @Override
  public Collection<Tier> getAllTiers() {
    return TierSortingRegistry.getSortedTiers();
  }

  @Override
  public int getLevel(Tier tier) {
    return tier == null || tier == TierUtils.NO_TIER ? -1 : TierSortingRegistry.getTiersLowerThan(tier).size() + 1;
  }

  @Override
  public @Nullable Tier getTier(ResourceLocation reference) {
    return TierSortingRegistry.byName(reference);
  }

  @Override
  public @Nullable Tier getTier(String reference) {
    return TierUtils.NO_TIER_REFERENCE.equalsIgnoreCase(reference) ? TierUtils.NO_TIER : ResourceLocation.isValidResourceLocation(reference) ? TierSortingRegistry.byName(new ResourceLocation(reference)) : null;
  }

  @Override
  public boolean matches(Tier tier, String reference) {
    ResourceLocation id = TierSortingRegistry.getName(tier);
    return tier.toString().equalsIgnoreCase(reference) || id != null && id.toString().equalsIgnoreCase(reference);
  }
}
