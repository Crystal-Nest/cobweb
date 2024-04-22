package it.crystalnest.cobweb.api.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.function.Function;

public abstract class CobwebConfig {
  private static final HashMap<String, Pair<CobwebConfig, ModConfigSpec>> CONFIGS = new HashMap<>();

  protected CobwebConfig(ModConfigSpec.Builder builder) {
    this.define(builder);
    builder.build();
  }

  protected static void register(String modId, ConfigType type, Function<ModConfigSpec.Builder, CobwebConfig> constructor) {
    CONFIGS.put(getId(modId, type), configure(constructor));
  }

  @SuppressWarnings("unchecked")
  protected static <T extends CobwebConfig> T getConfig(String modId, ConfigType type) {
    return (T) CONFIGS.get(getId(modId, type)).getLeft();
  }

  protected static ModConfigSpec getSpec(String modId, ConfigType type) {
    return CONFIGS.get(getId(modId, type)).getRight();
  }

  protected static String getId(String modId, ConfigType type) {
    return modId + "_" + type;
  }

  private static Pair<CobwebConfig, ModConfigSpec> configure(Function<ModConfigSpec.Builder, CobwebConfig> constructor) {
    return new ModConfigSpec.Builder().configure(constructor);
  }

  /**
   * Utility method to actually load the class and register the config. <br/>
   * Generally, it can be empty and do nothing. <br/>
   * Needs to be called in your mod loader.
   */
  public void register() {
    // Empty on purpose.
  }

  protected abstract void define(ModConfigSpec.Builder builder);

  protected boolean stringListValidator(Object element) {
    return element instanceof String string && !string.isBlank();
  }
}
