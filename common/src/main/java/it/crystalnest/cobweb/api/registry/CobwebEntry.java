package it.crystalnest.cobweb.api.registry;

import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Loader agnostic handle for a registered game object, backed by a native reference holder.
 * Obtain entries through {@link CobwebRegister}; use vanilla holders for objects outside registration.
 *
 * @param <T> game object type.
 */
public abstract class CobwebEntry<T> extends Holder.Reference<T> implements Supplier<T> {
  /**
   * Wrapped holder. Its value may still be awaiting registration.
   */
  private final Holder.Reference<T> holder;

  /**
   * The registration key must already be bound, but the value may still be awaiting registration.
   *
   * @param holder native reference holder for the registration.
   */
  @ApiStatus.Internal
  protected CobwebEntry(Holder.Reference<T> holder) {
    // Keep the registration key without resolving the value or replacing the native holder's owner.
    super(Type.STAND_ALONE, null, Objects.requireNonNull(holder).key(), null);
    this.holder = holder;
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
    return holder.key();
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
    holder.bindComponents(components);
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
