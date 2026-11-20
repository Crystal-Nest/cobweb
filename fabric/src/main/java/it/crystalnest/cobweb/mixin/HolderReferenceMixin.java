package it.crystalnest.cobweb.mixin;

import it.crystalnest.cobweb.api.registry.CobwebEntry;
import net.minecraft.core.Holder;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Injects into {@link Holder.Reference} to recognize Cobweb wrappers of the same native holder.
 */
@Mixin(Holder.Reference.class)
public abstract class HolderReferenceMixin {
  /**
   * Overrides the inherited {@link Object#equals(Object)} implementation.<br>
   * Recognizes wrappers of this holder while retaining identity equality between native holders and native identity hashing.
   *
   * @param other object to compare.
   * @return whether the object is this holder or a Cobweb wrapper of it.
   */
  @Override
  public boolean equals(Object other) {
    return this == other || other instanceof CobwebEntry<?> entry && entry.holder() == (Object) this;
  }
}
