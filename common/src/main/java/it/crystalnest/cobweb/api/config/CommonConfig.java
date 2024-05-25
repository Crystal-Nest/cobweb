package it.crystalnest.cobweb.api.config;

import it.crystalnest.cobweb.platform.Services;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * Common side configuration.
 */
public abstract class CommonConfig extends CobwebConfig {
  /**
   * @param builder configuration builder.
   */
  protected CommonConfig(ModConfigSpec.Builder builder) {
    super(builder);
  }

  /**
   * Instantiates and registers for the mod with the specified mod ID a new common configuration using the given constructor.
   *
   * @param modId mod ID.
   * @param constructor mod configuration constructor, should always be the calling class constructor, e.g. {@code register(MOD_ID, MyConfig::new);}
   * @param <T> class calling the method.
   * @return the new common configuration.
   */
  protected static <T extends CommonConfig> T register(String modId, Function<ModConfigSpec.Builder, T> constructor) {
    register(modId, ConfigType.COMMON, constructor);
    Services.CONFIG.registerCommonConfig(modId, getSpec(modId));
    return getConfig(modId);
  }

  /**
   * Returns the common configuration for the specified mod.
   *
   * @param modId mod ID.
   * @param <T> common config subclass.
   * @return common configuration for the specified mod or {@code null}.
   */
  @Nullable
  protected static <T extends CommonConfig> T getConfig(String modId) {
    return getConfig(modId, ConfigType.COMMON);
  }

  /**
   * Returns the common configuration specification for the specified mod.
   *
   * @param modId mod ID.
   * @return common configuration specification for the specified mod or {@code null}.
   */
  @Nullable
  protected static ModConfigSpec getSpec(String modId) {
    return getSpec(modId, ConfigType.COMMON);
  }
}
