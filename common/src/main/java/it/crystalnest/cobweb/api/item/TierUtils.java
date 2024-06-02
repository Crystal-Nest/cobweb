package it.crystalnest.cobweb.api.item;

import it.crystalnest.cobweb.platform.Services;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Utility methods for {@link Tier Tiers}.
 */
public final class TierUtils {
  /**
   * Reference for {@link #NO_TIER}.
   */
  public static final String NO_TIER_REFERENCE = "none";

  /**
   * Only tier to have {@code -1} level.
   */
  public static final Tier NO_TIER = new Tier() {
    @Override
    public int getUses() {
      return -1;
    }

    @Override
    public float getSpeed() {
      return -1;
    }

    @Override
    public float getAttackDamageBonus() {
      return -1;
    }

    @Override
    public int getLevel() {
      return -1;
    }

    @Override
    public int getEnchantmentValue() {
      return -1;
    }

    @Override
    @NotNull
    public Ingredient getRepairIngredient() {
      return Ingredient.EMPTY;
    }
  };

  private TierUtils() {}

  /**
   * Returns the list of all available tool tiers.
   *
   * @return list of all available tool tiers.
   */
  public static Collection<Tier> getAllTiers() {
    return Services.TOOL_TIERS.getAllTiers();
  }

  /**
   * Returns the tool tier referenced by the given {@link ResourceLocation}.
   *
   * @param reference tier reference.
   * @return tool tier or {@code null} if the reference is not valid.
   */
  @Nullable
  public static Tier getTier(ResourceLocation reference) {
    return Services.TOOL_TIERS.getTier(reference);
  }

  /**
   * Returns the tool tier referenced by the given string.
   *
   * @param reference tier reference.
   * @return tool tier or {@code null} if the reference is not valid.
   */
  @Nullable
  public static Tier getTier(String reference) {
    return Services.TOOL_TIERS.getTier(reference);
  }

  /**
   * Compares the two given tiers, abiding to the usual compare semantics.<br>
   * {@code 0} if the tiers are equal, {@code < 0} if {@code tier1} is lower than {@code tier2}, {@code > 0} if {@code tier1} is higher than {@code tier2}
   *
   * @param item1 first tool.
   * @param item2 second tool.
   * @return comparison result.
   */
  public static int compare(Item item1, Item item2) {
    return item1 instanceof TieredItem tiered1 && item2 instanceof TieredItem tiered2 ? compare(tiered1, tiered2) : 0;
  }

  /**
   * Compares the two given tiers, abiding to the usual compare semantics.<br>
   * {@code 0} if the tiers are equal, {@code < 0} if {@code tier1} is lower than {@code tier2}, {@code > 0} if {@code tier1} is higher than {@code tier2}
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
   * {@code 0} if the tiers are equal, {@code < 0} if {@code tier1} is lower than {@code tier2}, {@code > 0} if {@code tier1} is higher than {@code tier2}
   *
   * @param tier1 first tier.
   * @param tier2 second tier.
   * @return comparison result.
   */
  public static int compare(Tier tier1, Tier tier2) {
    return Services.TOOL_TIERS.compare(tier1, tier2);
  }

  /**
   * Compares the two given tiers, abiding to the usual compare semantics.<br>
   * {@code 0} if the tiers are equal, {@code < 0} if {@code tier1} is lower than {@code tier2}, {@code > 0} if {@code tier1} is higher than {@code tier2}
   *
   * @param reference1 first tier reference.
   * @param reference2 second tier reference.
   * @return comparison result.
   */
  public static int compare(ResourceLocation reference1, ResourceLocation reference2) {
    return compare(getTier(reference1), getTier(reference2));
  }

  /**
   * Compares the two given tiers, abiding to the usual compare semantics.<br>
   * {@code 0} if the tiers are equal, {@code < 0} if {@code tier1} is lower than {@code tier2}, {@code > 0} if {@code tier1} is higher than {@code tier2}
   *
   * @param reference1 first tier reference.
   * @param reference2 second tier reference.
   * @return comparison result.
   */
  public static int compare(String reference1, String reference2) {
    return compare(getTier(reference1), getTier(reference2));
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
   * Returns the numeric level of the given tool tier.<br>
   * {@code -1} is reserved.
   *
   * @param item tool.
   * @return tier level.
   */
  public static int getLevel(Item item) {
    return item instanceof TieredItem tiered ? getLevel(tiered) : NO_TIER.getLevel();
  }

  /**
   * Returns the numeric level of the given tool tier.<br>
   * {@code -1} is reserved.
   *
   * @param item tool.
   * @return tier level.
   */
  public static int getLevel(TieredItem item) {
    return getLevel(item.getTier());
  }

  /**
   * Returns the numeric level of the given tool tier.<br>
   * {@code -1} is reserved for {@link #NO_TIER}.
   *
   * @param tier tool tier.
   * @return tier level.
   */
  public static int getLevel(@Nullable Tier tier) {
    return Services.TOOL_TIERS.getLevel(tier);
  }

  /**
   * Returns the numeric level of the given tool tier.<br>
   * {@code -1} is reserved for {@link #NO_TIER}.
   *
   * @param reference tier reference.
   * @return tier level.
   */
  public static int getLevel(ResourceLocation reference) {
    return getLevel(Services.TOOL_TIERS.getTier(reference));
  }

  /**
   * Returns the numeric level of the given tool tier.<br>
   * {@code -1} is reserved for {@link #NO_TIER}.
   *
   * @param reference tier reference.
   * @return tier level.
   */
  public static int getLevel(String reference) {
    return getLevel(Services.TOOL_TIERS.getTier(reference));
  }

  /**
   * Checks whether the given reference represents the given tier.
   *
   * @param tier tier.
   * @param reference tier reference.
   * @return whether the reference matches the tier.
   */
  public static boolean matches(Tier tier, String reference) {
    return Services.TOOL_TIERS.matches(tier, reference);
  }
}
