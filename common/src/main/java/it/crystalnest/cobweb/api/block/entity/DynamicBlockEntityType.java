package it.crystalnest.cobweb.api.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Block entity type that allows a dynamic list of valid blocks.
 *
 * @param <T> block entity.
 */
public class DynamicBlockEntityType<T extends BlockEntity> extends BlockEntityType<T> {
  /**
   * Validator function that determines whether this block entity can be applied to a block state.
   */
  private final Function<BlockState, Boolean> validator;

  /**
   * @param supplier {@link BlockEntitySupplier} for the custom block entity.
   * @param validator validator function that determines whether this block entity can be applied to a block state.
   */
  public DynamicBlockEntityType(BlockEntitySupplier<? extends T> supplier, Function<BlockState, Boolean> validator) {
    super(supplier, Set.of());
    this.validator = validator;
  }

  /**
   * Creates a {@link DynamicBlockEntityType}.<br>
   * Equivalent to the public constructor, but does not require access to the {@link BlockEntitySupplier} class.
   *
   * @param supplier {@link BlockEntitySupplier} for the custom block entity.
   * @param validator validator function that determines whether this block entity can be applied to a block state.
   * @return {@link DynamicBlockEntityType}.
   * @param <E> block entity type.
   */
  public static <E extends BlockEntity> DynamicBlockEntityType<E> of(BiFunction<BlockPos, BlockState, ? extends E> supplier, Function<BlockState, Boolean> validator) {
    return new DynamicBlockEntityType<>(supplier::apply, validator);
  }

  @Override
  public boolean isValid(@NotNull BlockState state) {
    return validator.apply(state);
  }
}
