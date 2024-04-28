package it.crystalnest.cobweb.api.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Cobweb standardized configuration.
 */
public abstract class CobwebConfig {
  /**
   * Map of all mods with their registered pairs of configuration and specification.
   */
  private static final Map<String, Pair<? extends CobwebConfig, ModConfigSpec>> CONFIGS = new HashMap<>();

  /**
   * @param builder configuration builder.
   */
  protected CobwebConfig(ModConfigSpec.Builder builder) {
    this.define(builder);
    builder.build();
  }

  /**
   * Instantiates and registers for the mod with the specified mod ID a new configuration of the given type using the given constructor.
   *
   * @param modId mod ID.
   * @param type configuration type.
   * @param constructor mod configuration constructor, should always be the calling class constructor, e.g. {@code register(MOD_ID, MyConfig::new);}
   * @param <T> class calling the method.
   */
  protected static <T extends CobwebConfig> void register(String modId, ConfigType type, Function<ModConfigSpec.Builder, T> constructor) {
    CONFIGS.put(getId(modId, type), new ModConfigSpec.Builder().configure(constructor));
  }

  /**
   * Returns the cobweb configuration for the specified mod.
   *
   * @param modId mod ID.
   * @param type configuration type.
   * @param <T> cobweb config subclass.
   * @return cobweb configuration for the specified mod or {@code null}.
   */
  @Nullable
  @SuppressWarnings("unchecked")
  protected static <T extends CobwebConfig> T getConfig(String modId, ConfigType type) {
    return CONFIGS.containsKey(getId(modId, type)) ? (T) CONFIGS.get(getId(modId, type)).getLeft() : null;
  }

  /**
   * Returns the cobweb configuration specification for the specified mod.
   *
   * @param modId mod ID.
   * @param type configuration type.
   * @return cobweb configuration specification for the specified mod or {@code null}.
   */
  @Nullable
  protected static ModConfigSpec getSpec(String modId, ConfigType type) {
    return CONFIGS.containsKey(getId(modId, type)) ? CONFIGS.get(getId(modId, type)).getRight() : null;
  }

  /**
   * Returns the ID for a mod and a configuration type.
   *
   * @param modId mod ID.
   * @param type configuration type.
   * @return cobweb configuration ID.
   */
  protected static String getId(String modId, ConfigType type) {
    return modId + "_" + type;
  }

  /**
   * Utility method to actually load the class and register the config. <br/>
   * Generally, it can be empty and do nothing. <br/>
   * Needs to be called in your mod loader.
   */
  public void register() {
    // Empty on purpose.
  }

  /**
   * Defines the configuration specification using the provided builder.
   *
   * @param builder configuration builder.
   */
  protected abstract void define(ModConfigSpec.Builder builder);

  /**
   * Validator for a configuration item typed as a list of strings.
   *
   * @param element element.
   * @return whether the element is valid.
   */
  protected boolean stringListValidator(Object element) {
    return element instanceof String string && !string.isBlank();
  }
}
