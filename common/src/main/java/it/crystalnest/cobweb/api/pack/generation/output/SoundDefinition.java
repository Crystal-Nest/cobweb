package it.crystalnest.cobweb.api.pack.generation.output;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Describes one client sound event, using Minecraft's sound definition format.
 *
 * @param sounds ordered samples or event references.
 * @param replace whether Minecraft should discard this event's lower-priority definitions.
 * @param subtitle optional subtitle translation key.
 */
public record SoundDefinition(List<Entry> sounds, boolean replace, @Nullable String subtitle) {
  /**
   * @param sounds samples or event references.
   * @param replace whether to discard lower-priority definitions.
   * @param subtitle optional subtitle translation key.
   */
  public SoundDefinition {
    sounds = List.copyOf(sounds);
  }

  /**
   * Describes one sample or event reference and its playback properties.
   * Volume and pitch must be finite and positive. Weight must be positive.
   *
   * @param id sound file identifier without its directory and extension, or event identifier.
   * @param event whether the identifier refers to another event.
   * @param volume positive volume multiplier.
   * @param pitch positive pitch multiplier.
   * @param weight positive selection weight.
   * @param stream whether to stream the sample.
   * @param preload whether to preload the sample.
   * @param attenuationDistance attenuation distance.
   */
  public record Entry(Identifier id, boolean event, float volume, float pitch, int weight, boolean stream, boolean preload, int attenuationDistance) {
    /**
     * @param id sample or event identifier.
     * @param event whether the identifier refers to another event.
     * @param volume positive finite volume multiplier.
     * @param pitch positive finite pitch multiplier.
     * @param weight positive selection weight.
     * @param stream whether to stream the sample.
     * @param preload whether to preload the sample.
     * @param attenuationDistance attenuation distance.
     */
    public Entry {
      Objects.requireNonNull(id);
      if (!Float.isFinite(volume) || volume <= 0 || !Float.isFinite(pitch) || pitch <= 0 || weight <= 0) {
        throw new IllegalArgumentException("Sound volume, pitch and weight must be finite and positive");
      }
    }

    /**
     * References an encoded audio file with Minecraft's default playback properties.
     *
     * @param id sample identifier without its directory and extension.
     * @return a file entry with default properties.
     */
    public static Entry file(Identifier id) {
      return new Entry(id, false, 1, 1, 1, false, false, 16);
    }

    /**
     * References another sound event with Minecraft's default playback properties.
     *
     * @param id referenced event.
     * @return an event entry with default properties.
     */
    public static Entry event(Identifier id) {
      return new Entry(id, true, 1, 1, 1, false, false, 16);
    }
  }
}
