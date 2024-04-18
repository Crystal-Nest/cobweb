package it.crystalnest.cobweb.api.config;

import it.crystalnest.cobweb.Constants;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.function.Function;

public abstract class ServerConfig extends CobwebConfig {
  protected ServerConfig(ModConfigSpec.Builder builder) {
    super(builder);
  }

  protected static <T extends ServerConfig> T register(String modId, Function<ModConfigSpec.Builder, CobwebConfig> constructor) {
    register(modId, ConfigType.SERVER, constructor);
    Constants.CONFIG.registerServerConfig(getId(modId, ConfigType.SERVER), getSpec(modId));
    return getConfig(modId);
  }

  protected static <T extends CobwebConfig> T getConfig(String modId) {
    return getConfig(modId, ConfigType.SERVER);
  }

  protected static ModConfigSpec getSpec(String modId) {
    return getSpec(modId, ConfigType.SERVER);
  }
}
