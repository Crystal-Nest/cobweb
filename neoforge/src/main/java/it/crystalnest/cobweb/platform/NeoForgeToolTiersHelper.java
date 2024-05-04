package it.crystalnest.cobweb.platform;

import it.crystalnest.cobweb.platform.services.ToolTiersHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Tier;
import net.neoforged.neoforge.common.TierSortingRegistry;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Forge tool tiers helper.
 */
public final class NeoForgeToolTiersHelper implements ToolTiersHelper {
  @Override
  public Collection<Tier> getAllTiers() {
    return TierSortingRegistry.getSortedTiers();
  }

  @Override
  public int getLevel(Tier tier) {
    return tier == NO_TIER ? -1 : TierSortingRegistry.getTiersLowerThan(tier).size() + 1;
  }

  @Override
  public @Nullable Tier getTier(ResourceLocation reference) {
    return TierSortingRegistry.byName(reference);
  }

  @Override
  public @Nullable Tier getTier(String reference) {
    return NO_TIER_REFERENCE.equalsIgnoreCase(reference) ? NO_TIER : ResourceLocation.isValidResourceLocation(reference) ? TierSortingRegistry.byName(new ResourceLocation(reference)) : null;
  }

  @Override
  public int compare(Tier tier1, Tier tier2) {
    return getLevel(tier1) - getLevel(tier2);
  }

  @Override
  public boolean matches(Tier tier, String reference) {
    ResourceLocation id = TierSortingRegistry.getName(tier);
    return tier.toString().equalsIgnoreCase(reference) || id != null && id.toString().equalsIgnoreCase(reference);
  }
}
