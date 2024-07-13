package it.crystalnest.cobweb.api.registry;

import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Loader agnostic register.
 *
 * @param <R> register type.
 */
public interface CobwebRegister<R> {
  /**
   * Registers the game object returned by the given supplier.
   *
   * @param name game object name.
   * @param supplier game object supplier.
   * @param <T> game object type.
   * @return registered game object holder.
   */
  <T extends R> CobwebEntry<T> register(String name, Supplier<? extends T> supplier);

  /**
   * Wrapper around {@link Holder} to implement {@link Supplier}.
   *
   * @param <T> game object type.
   */
  final class CobwebEntry<T> implements Holder<T>, Supplier<T> {
    /**
     * Wrapped instance of {@link Holder}.
     */
    private final Holder<T> holder;

    /**
     * @param holder {@link #holder}.
     */
    public CobwebEntry(Holder<T> holder) {
      this.holder = holder;
    }

    @NotNull
    @Override
    public T get() {
      return value();
    }

    @NotNull
    @Override
    public T value() {
      return holder.value();
    }

    @Override
    public boolean isBound() {
      return holder.isBound();
    }

    @Override
    public boolean is(@NotNull ResourceLocation key) {
      return holder.is(key);
    }

    @Override
    public boolean is(@NotNull ResourceKey<T> key) {
      return holder.is(key);
    }

    @Override
    public boolean is(@NotNull Predicate<ResourceKey<T>> predicate) {
      return holder.is(predicate);
    }

    @Override
    public boolean is(@NotNull TagKey<T> key) {
      return holder.is(key);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean is(Holder<T> holder) {
      return holder.is(holder);
    }

    @NotNull
    @Override
    public Stream<TagKey<T>> tags() {
      return holder.tags();
    }

    @NotNull
    @Override
    public Either<ResourceKey<T>, T> unwrap() {
      return holder.unwrap();
    }

    @NotNull
    @Override
    public Optional<ResourceKey<T>> unwrapKey() {
      return holder.unwrapKey();
    }

    @NotNull
    @Override
    public Kind kind() {
      return holder.kind();
    }

    @Override
    public boolean canSerializeIn(@NotNull HolderOwner<T> owner) {
      return holder.canSerializeIn(owner);
    }

    @NotNull
    @Override
    public String getRegisteredName() {
      return holder.getRegisteredName();
    }
  }
}
