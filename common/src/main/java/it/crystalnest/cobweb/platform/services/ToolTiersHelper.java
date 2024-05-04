package it.crystalnest.cobweb.platform.services;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Platform specific tool tiers helper.
 */
public interface ToolTiersHelper {

  /**
   * Reference for {@link #NO_TIER}.
   */
  String NO_TIER_REFERENCE = "none";

  /**
   * Only tier to have {@code -1} level.
   */
  Tier NO_TIER = new Tier() {
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

  /**
   * Returns the list of all available tool tiers.
   *
   * @return list of all available tool tiers.
   */
  Collection<Tier> getAllTiers();

  /**
   * Returns the numeric level of the given tool tier. <br>
   * {@code -1} is reserved for {@link #NO_TIER}.
   *
   * @param tier tool tier.
   * @return tier level.
   */
  int getLevel(@Nullable Tier tier);

  /**
   * Returns the tool tier referenced by the given string.
   *
   * @param reference tier reference.
   * @return tool tier or {@code null} if the reference is not valid.
   */
  @Nullable
  Tier getTier(String reference);

  /**
   * Returns the tool tier referenced by the given {@link ResourceLocation}.
   *
   * @param reference tier reference.
   * @return tool tier or {@code null} if the reference is not valid.
   */
  @Nullable
  default Tier getTier(ResourceLocation reference) {
    return getTier(reference.toString());
  }

  /**
   * Compares the two given tiers, abiding to the usual compare semantics.
   * <br>
   * {@code 0} if the tiers are equal, {@code < 0} if {@code tier1} is lower than {@code tier2}, {@code > 0} i f{@code tier1} is higher than {@code tier2}
   *
   * @param tier1 first tier.
   * @param tier2 second tier.
   * @return comparison result.
   */
  int compare(@Nullable Tier tier1, @Nullable Tier tier2);

  /**
   * Checks whether the given reference represents the given tier.
   *
   * @param tier tier.
   * @param reference tier reference.
   * @return whether the reference matches the tier.
   */
  default boolean matches(Tier tier, ResourceLocation reference) {
    return matches(tier, reference.toString());
  }

  /**
   * Checks whether the given reference represents the given tier.
   *
   * @param tier tier.
   * @param reference tier reference.
   * @return whether the reference matches the tier.
   */
  boolean matches(Tier tier, String reference);
}
