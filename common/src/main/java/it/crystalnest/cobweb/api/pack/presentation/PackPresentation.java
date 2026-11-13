package it.crystalnest.cobweb.api.pack.presentation;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable partial replacement for a pack's visible title, description and icon.
 *
 * @param title title override, or null to keep the base title.
 * @param description description override, or null to keep the base description.
 * @param icon icon override, or null to keep the base icon.
 */
public record PackPresentation(@Nullable Component title, @Nullable Component description, @Nullable PackIcon icon) {
  /**
   * Captures the supplied presentation changes.
   *
   * @param title optional title component.
   * @param description optional description component.
   * @param icon optional icon source.
   */
  public PackPresentation {
    title = title == null ? null : title.copy();
    description = description == null ? null : description.copy();
  }

  /**
   * Returns the override as an independent component.
   *
   * @return title override, or null to keep the base title.
   */
  @Override
  @Nullable
  public Component title() {
    return title == null ? null : title.copy();
  }

  /**
   * Returns the override as an independent component.
   *
   * @return description override, or null to keep the base description.
   */
  @Override
  @Nullable
  public Component description() {
    return description == null ? null : description.copy();
  }

  /**
   * Creates a builder for an optional set of presentation changes.
   *
   * @return empty patch builder.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Collects the visible fields supplied by a presentation callback.
   */
  public static final class Builder {
    @Nullable
    private Component title;

    @Nullable
    private Component description;

    @Nullable
    private PackIcon icon;

    private Builder() {}

    /**
     * Replaces the displayed title.
     *
     * @param value title component.
     * @return this builder.
     */
    public Builder title(Component value) {
      title = value.copy();
      return this;
    }

    /**
     * Replaces the displayed title with a translation key.
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
     * @param value description component.
     * @return this builder.
     */
    public Builder description(Component value) {
      description = value.copy();
      return this;
    }

    /**
     * Replaces the displayed description with a translation key.
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
     * @param value icon resource.
     * @return this builder.
     */
    public Builder icon(PackIcon value) {
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
