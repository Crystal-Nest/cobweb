package it.crystalnest.cobweb.api.registry;

import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Wrapper around {@link Holder} to implement {@link Supplier}.
 *
 * @param <T> game object type.
 */
public class CobwebEntry<T> extends Holder.Reference<T> implements Supplier<T> {
  /**
   * Wrapped holder. Its value may still be awaiting registration.
   */
  private final Holder<T> holder;

  /**
   * @param holder wrapped instance of {@link Holder}.
   */
  public CobwebEntry(Holder<T> holder) {
    // All holder operations delegate, so constructing an entry does not resolve an unbound holder.
    super(Type.STAND_ALONE, null, null, null);
    this.holder = Objects.requireNonNull(holder);
  }

  /**
   * Returns the wrapped holder.
   *
   * @return wrapped holder.
   */
  public Holder<T> holder() {
    return holder;
  }

  /**
   * Exposes the underlying holder to NeoForge's delegating-holder lookup without a common loader dependency.
   *
   * @return wrapped holder.
   */
  public Holder<T> getDelegate() {
    return holder;
  }

  @NotNull
  @Override
  public ResourceKey<T> key() {
    return holder.unwrapKey().orElseThrow();
  }

  @Override
  public boolean equals(Object other) {
    return this == other || other instanceof CobwebEntry<?> entry && holder.equals(entry.holder);
  }

  @Override
  public int hashCode() {
    return holder.hashCode();
  }

  @Override
  public String toString() {
    return "CobwebEntry[holder=" + holder + "]";
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
  public boolean areComponentsBound() {
    return holder.areComponentsBound();
  }

  @Override
  public boolean is(@NotNull Identifier key) {
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
  public boolean is(@NotNull Holder<T> holder) {
    return this.holder.is(holder);
  }

  @NotNull
  @Override
  public Stream<TagKey<T>> tags() {
    return holder.tags();
  }

  @Override
  public @NonNull DataComponentMap components() {
    return holder.components();
  }

  @Override
  public void bindComponents(DataComponentMap components) {
    if (holder instanceof Holder.Reference<T> reference) {
      reference.bindComponents(components);
    } else {
      throw new UnsupportedOperationException("Cannot bind components to a direct holder");
    }
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
