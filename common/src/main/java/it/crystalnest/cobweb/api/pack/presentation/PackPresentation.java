package it.crystalnest.cobweb.api.pack.presentation;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Describes optional title, description, and icon overrides for a pack in the selection editor.
 * Omitted fields retain their effective base values.
 *
 * @param title title override, or {@code null} to keep the base title.
 * @param description description override, or {@code null} to keep the base description.
 * @param icon icon override, or {@code null} to keep the base icon.
 */
public record PackPresentation(@Nullable Component title, @Nullable Component description, @Nullable PackIcon icon) {
  /**
   * @param title title component to copy, or {@code null} to keep the base title.
   * @param description description component to copy, or {@code null} to keep the base description.
   * @param icon icon source, or {@code null} to keep the base icon.
   */
  public PackPresentation {
    title = title == null ? null : title.copy();
    description = description == null ? null : description.copy();
  }

  /**
   * Returns a copy of the title override.
   *
   * @return title override, or {@code null} to keep the base title.
   */
  @Override
  @Nullable
  public Component title() {
    return title == null ? null : title.copy();
  }

  /**
   * Returns a copy of the description override.
   *
   * @return description override, or {@code null} to keep the base description.
   */
  @Override
  @Nullable
  public Component description() {
    return description == null ? null : description.copy();
  }

  /**
   * Returns the icon override.
   *
   * @return icon source, or {@code null} to keep the base icon.
   */
  public @Nullable PackIcon icon() {
    return icon;
  }

  /**
   * Creates an empty builder that inherits all base fields.
   *
   * @return new presentation builder.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Collects the visible fields supplied by a presentation callback.
   */
  public static final class Builder {
    /**
     * Title override, or {@code null} to keep the base title.
     */
    @Nullable
    private Component title;

    /**
     * Description override, or {@code null} to keep the base description.
     */
    @Nullable
    private Component description;

    /**
     * Icon override, or {@code null} to keep the base icon.
     */
    @Nullable
    private PackIcon icon;

    private Builder() {}

    /**
     * Replaces the displayed title.
     *
     * @param value title component to copy.
     * @return this builder.
     * @throws NullPointerException if the component is {@code null}.
     */
    public Builder title(Component value) {
      title = value.copy();
      return this;
    }

    /**
     * Replaces the displayed title with a translatable component.
     *
     * @param translationKey title key.
     * @return this builder.
     */
    public Builder title(String translationKey) {
      return title(Component.translatable(translationKey));
    }

    /**
     * Replaces the displayed description.
     *
     * @param value description component to copy.
     * @return this builder.
     * @throws NullPointerException if the component is {@code null}.
     */
    public Builder description(Component value) {
      description = value.copy();
      return this;
    }

    /**
     * Replaces the displayed description with a translatable component.
     *
     * @param translationKey description key.
     * @return this builder.
     */
    public Builder description(String translationKey) {
      return description(Component.translatable(translationKey));
    }

    /**
     * Replaces the displayed icon.
     *
     * @param value icon source, or {@code null} to keep the base icon.
     * @return this builder.
     */
    public Builder icon(@Nullable PackIcon value) {
      icon = value;
      return this;
    }

    /**
     * Creates the immutable presentation patch.
     *
     * @return presentation patch.
     */
    public PackPresentation build() {
      return new PackPresentation(title, description, icon);
    }
  }
}
