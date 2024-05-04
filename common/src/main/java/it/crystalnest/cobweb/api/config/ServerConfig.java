package it.crystalnest.cobweb.api.config;

import it.crystalnest.cobweb.platform.Services;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * Server side configuration.
 */
public abstract class ServerConfig extends CobwebConfig {
  /**
   * @param builder configuration builder.
   */
  protected ServerConfig(ModConfigSpec.Builder builder) {
    super(builder);
  }

  /**
   * Instantiates and registers for the mod with the specified mod ID a new server configuration using the given constructor.
   *
   * @param modId mod ID.
   * @param constructor mod configuration constructor, should always be the calling class constructor, e.g. {@code register(MOD_ID, MyConfig::new);}
   * @param <T> class calling the method.
   * @return the new server configuration.
   */
  protected static <T extends ServerConfig> T register(String modId, Function<ModConfigSpec.Builder, T> constructor) {
    register(modId, ConfigType.SERVER, constructor);
    Services.CONFIG.registerServerConfig(getId(modId, ConfigType.SERVER), getSpec(modId));
    return getConfig(modId);
  }

  /**
   * Returns the server configuration for the specified mod.
   *
   * @param modId mod ID.
   * @param <T> server config subclass.
   * @return server configuration for the specified mod or {@code null}.
   */
  @Nullable
  protected static <T extends ServerConfig> T getConfig(String modId) {
    return getConfig(modId, ConfigType.SERVER);
  }

  /**
   * Returns the server configuration specification for the specified mod.
   *
   * @param modId mod ID.
   * @return server configuration specification for the specified mod or {@code null}.
   */
  @Nullable
  protected static ModConfigSpec getSpec(String modId) {
    return getSpec(modId, ConfigType.SERVER);
  }
}
