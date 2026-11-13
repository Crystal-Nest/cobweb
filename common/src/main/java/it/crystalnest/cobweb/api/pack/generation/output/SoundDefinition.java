package it.crystalnest.cobweb.api.pack.generation.output;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.ApiStatus;
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
   * Captures the ordered entries of a complete sound event.
   *
   * @param sounds samples or event references.
   * @param replace whether to discard lower-priority definitions.
   * @param subtitle optional subtitle translation key.
   */
  public SoundDefinition {
    sounds = List.copyOf(sounds);
  }

  /**
   * Encodes this event for inclusion in its namespace's sound definitions.
   *
   * @return JSON object for the complete sound event.
   */
  @ApiStatus.Internal
  public JsonObject toJson() {
    var json = new JsonObject();
    if (subtitle != null) {
      json.addProperty("subtitle", subtitle);
    }
    json.addProperty("replace", replace);
    var entries = new JsonArray();
    for (var sound : sounds) {
      var entry = new JsonObject();
      entry.addProperty("name", sound.id().toString());
      entry.addProperty("type", sound.event() ? "event" : "file");
      entry.addProperty("volume", sound.volume());
      entry.addProperty("pitch", sound.pitch());
      entry.addProperty("weight", sound.weight());
      entry.addProperty("stream", sound.stream());
      entry.addProperty("preload", sound.preload());
      entry.addProperty("attenuation_distance", sound.attenuationDistance());
      entries.add(entry);
    }
    json.add("sounds", entries);
    return json;
  }

  /**
   * Describes one sample or event reference and its playback properties.
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
     * Validates a sample or event reference for Minecraft's sound parser.
     *
     * @param id sample or event identifier.
     * @param event whether the identifier refers to another event.
     * @param volume positive finite volume multiplier.
     * @param pitch positive finite pitch multiplier.
     * @param weight positive selection weight.
     * @param stream whether to stream the sample.
     * @param preload whether to preload the sample.
     * @param attenuationDistance attenuation distance.
     * @throws IllegalArgumentException if the volume, pitch or weight is invalid.
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
