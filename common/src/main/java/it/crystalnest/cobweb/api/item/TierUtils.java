package it.crystalnest.cobweb.api.item;

import it.crystalnest.cobweb.Constants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TieredItem;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Utility methods for {@link Tier Tiers}.
 */
public final class TierUtils {
  private TierUtils() {}

  /**
   * Returns the list of all available tool tiers.
   *
   * @return list of all available tool tiers.
   */
  public static Collection<Tier> getAllTiers() {
    return Constants.TOOL_TIERS.getAllTiers();
  }

  /**
   * Compares the two given tiers, abiding to the usual compare semantics.<br>
   * {@code 0} if the tiers are equal, {@code < 0} if {@code tier1} is lower than {@code tier2}, {@code > 0} i f{@code tier1} is higher than {@code tier2}
   *
   * @param tier1 first tool.
   * @param tier2 second tool.
   * @return comparison result.
   */
  public static int compare(Tier tier1, Tier tier2) {
    return Constants.TOOL_TIERS.compare(tier1, tier2);
  }

  /**
   * Compares the two given tiers, abiding to the usual compare semantics.<br>
   * {@code 0} if the tiers are equal, {@code < 0} if {@code tier1} is lower than {@code tier2}, {@code > 0} i f{@code tier1} is higher than {@code tier2}
   *
   * @param item1 first tool.
   * @param item2 second tool.
   * @return comparison result.
   */
  public static int compare(TieredItem item1, TieredItem item2) {
    return compare(item1.getTier(), item2.getTier());
  }

  /**
   * Compares the two given tiers, abiding to the usual compare semantics.<br>
   * {@code 0} if the tiers are equal, {@code < 0} if {@code tier1} is lower than {@code tier2}, {@code > 0} i f{@code tier1} is higher than {@code tier2}
   *
   * @param item1 first item.
   * @param item2 second item.
   * @return comparison result.
   */
  public static int compare(Item item1, Item item2) {
    return item1 instanceof TieredItem tiered1 && item2 instanceof TieredItem tiered2 ? compare(tiered1, tiered2) : 0;
  }

  /**
   * Checks whether the given reference represents a tier in the given tier list.
   *
   * @param tiers list of tiers.
   * @param reference tier reference.
   * @return whether the tier is in the list.
   */
  public static boolean isIn(Collection<Tier> tiers, String reference) {
    return tiers.stream().anyMatch(tier -> matches(tier, reference));
  }

  /**
   * Checks whether the given reference represents a tier in the given tier list.
   *
   * @param tiers list of tiers.
   * @param reference tier reference.
   * @return whether the tier is in the list.
   */
  public static boolean isIn(Collection<Tier> tiers, ResourceLocation reference) {
    return isIn(tiers, reference.toString());
  }

  /**
   * Checks whether the given tier is in the given tier list.
   *
   * @param tiers list of tiers.
   * @param tier tier.
   * @return whether the tier is in the list.
   */
  public static boolean isIn(Collection<Tier> tiers, Tier tier) {
    return isIn(tiers, tier.toString());
  }

  /**
   * Returns the numeric level of the given tool tier. <br>
   * {@code -1} is reserved.
   *
   * @param tier tool tier.
   * @return tier level.
   */
  public static int getLevel(@Nullable Tier tier) {
    return tier == null ? 0 : Constants.TOOL_TIERS.getLevel(tier);
  }

  /**
   * Returns the numeric level of the given tool tier. <br>
   * {@code -1} is reserved.
   *
   * @param reference tier reference.
   * @return tier level.
   */
  public static int getLevel(ResourceLocation reference) {
    return getLevel(Constants.TOOL_TIERS.getTier(reference));
  }

  /**
   * Returns the numeric level of the given tool tier. <br>
   * {@code -1} is reserved.
   *
   * @param reference tier reference.
   * @return tier level.
   */
  public static int getLevel(String reference) {
    return getLevel(Constants.TOOL_TIERS.getTier(reference));
  }

  /**
   * Checks whether the given reference represents the given tier.
   *
   * @param tier tier.
   * @param reference tier reference.
   * @return whether the reference matches the tier.
   */
  public static boolean matches(Tier tier, String reference) {
    return Constants.TOOL_TIERS.matches(tier, reference);
  }
}
