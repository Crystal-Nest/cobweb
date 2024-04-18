package it.crystalnest.cobweb.api.config;

import it.crystalnest.cobweb.Constants;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.function.Function;

public abstract class CommonConfig extends CobwebConfig {
  protected CommonConfig(ModConfigSpec.Builder builder) {
    super(builder);
  }

  protected static <T extends CommonConfig> T register(String modId, Function<ModConfigSpec.Builder, CobwebConfig> constructor) {
    register(modId, ConfigType.COMMON, constructor);
    Constants.CONFIG.registerCommonConfig(modId, getSpec(modId));
    return getConfig(modId);
  }

  protected static <T extends CobwebConfig> T getConfig(String modId) {
    return getConfig(modId, ConfigType.COMMON);
  }

  protected static ModConfigSpec getSpec(String modId) {
    return getSpec(modId, ConfigType.COMMON);
  }
}
