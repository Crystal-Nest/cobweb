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
  private final Function<BlockState, Boolean> validator;

  /**
   * @param supplier {@link BlockEntitySupplier} for the custom block entity.
   */
  public DynamicBlockEntityType(BlockEntitySupplier<? extends T> supplier, Function<BlockState, Boolean> validator) {
    //noinspection DataFlowIssue
    super(supplier, Set.of(), null);
    this.validator = validator;
  }

  /**
   * @param supplier {@link BlockEntitySupplier} for the custom block entity.
   */
  public DynamicBlockEntityType(BiFunction<BlockPos, BlockState, ? extends T> supplier, Function<BlockState, Boolean> validator) {
    this((BlockEntitySupplier<? extends T>) supplier::apply, validator);
  }

  @Override
  public boolean isValid(@NotNull BlockState state) {
    return validator.apply(state);
  }
}
