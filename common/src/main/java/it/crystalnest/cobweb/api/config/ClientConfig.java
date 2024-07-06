package it.crystalnest.cobweb.api.config;

import it.crystalnest.cobweb.platform.Services;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * Client side configuration.
 */
public abstract class ClientConfig extends CobwebConfig {
  /**
   * @param builder configuration builder.
   */
  protected ClientConfig(ModConfigSpec.Builder builder) {
    super(builder);
  }

  /**
   * Instantiates and registers for the mod with the specified mod ID a new client configuration using the given constructor.
   *
   * @param modId mod ID.
   * @param constructor mod configuration constructor, should always be the calling class constructor, e.g. {@code register(MOD_ID, MyConfig::new);}
   * @param <T> class calling the method.
   * @return the new client configuration.
   */
  protected static <T extends ClientConfig> T register(String modId, Function<ModConfigSpec.Builder, T> constructor) {
    register(modId, ConfigType.CLIENT, constructor);
    if (Services.CONFIG != null) {
      Services.CONFIG.registerClientConfig(getId(modId, ConfigType.CLIENT), getSpec(modId));
    } else {
      throw new NullPointerException("Make sure you are on NeoForge or the mod Forge Config API Port is installed!");
    }
    return getConfig(modId);
  }

  /**
   * Returns the client configuration for the specified mod.
   *
   * @param modId mod ID.
   * @param <T> client config subclass.
   * @return client configuration for the specified mod or {@code null}.
   */
  @Nullable
  protected static <T extends ClientConfig> T getConfig(String modId) {
    return getConfig(modId, ConfigType.CLIENT);
  }

  /**
   * Returns the client configuration specification for the specified mod.
   *
   * @param modId mod ID.
   * @return client configuration specification for the specified mod or {@code null}.
   */
  @Nullable
  protected static ModConfigSpec getSpec(String modId) {
    return getSpec(modId, ConfigType.CLIENT);
  }
}
