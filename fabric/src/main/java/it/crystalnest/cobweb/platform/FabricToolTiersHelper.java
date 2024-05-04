package it.crystalnest.cobweb.platform;

import it.crystalnest.cobweb.platform.services.ToolTiersHelper;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Fabric tool tiers helper.
 */
public final class FabricToolTiersHelper implements ToolTiersHelper {
  @Override
  public Collection<Tier> getAllTiers() {
    return List.of(Tiers.values());
  }

  @Override
  public int getLevel(Tier tier) {
    return tier.getLevel();
  }

  @Override
  public @Nullable Tier getTier(String reference) {
    return NO_TIER_REFERENCE.equalsIgnoreCase(reference) ? NO_TIER : getAllTiers().stream().filter(tier -> matches(tier, reference)).findFirst().orElse(null);
  }

  @Override
  public int compare(Tier tier1, Tier tier2) {
    return tier1.getLevel() - tier2.getLevel();
  }

  @Override
  public boolean matches(Tier tier, String reference) {
    return tier.toString().equalsIgnoreCase(reference);
  }
}
