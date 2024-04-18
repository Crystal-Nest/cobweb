package it.crystalnest.cobweb.api.config;

import it.crystalnest.cobweb.Constants;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.function.Function;

public abstract class ClientConfig extends CobwebConfig {
  protected ClientConfig(ModConfigSpec.Builder builder) {
    super(builder);
  }

  protected static <T extends ClientConfig> T register(String modId, Function<ModConfigSpec.Builder, CobwebConfig> constructor) {
    register(modId, ConfigType.CLIENT, constructor);
    Constants.CONFIG.registerClientConfig(getId(modId, ConfigType.CLIENT), getSpec(modId));
    return getConfig(modId);
  }

  protected static <T extends CobwebConfig> T getConfig(String modId) {
    return getConfig(modId, ConfigType.CLIENT);
  }

  protected static ModConfigSpec getSpec(String modId) {
    return getSpec(modId, ConfigType.CLIENT);
  }
}
