package it.crystalnest.cobweb.platform.services;

import net.neoforged.neoforge.common.ModConfigSpec;

public interface ConfigHelper {
  void registerCommonConfig(String modId, ModConfigSpec spec);

  void registerClientConfig(String modId, ModConfigSpec spec);

  void registerServerConfig(String modId, ModConfigSpec spec);

  void registerCommonConfig(ModConfigSpec spec);

  void registerClientConfig(ModConfigSpec spec);

  void registerServerConfig(ModConfigSpec spec);
}
