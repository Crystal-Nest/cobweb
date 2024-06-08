package it.crystalnest.cobweb.platform;

import it.crystalnest.cobweb.ModLoader;
import it.crystalnest.cobweb.api.registry.CobwebRegister;
import it.crystalnest.cobweb.platform.services.RegistryHelper;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.HashMap;

/**
 * NeoForge registration helper.
 */
public final class NeoForgeRegistryHelper extends RegistryHelper<NeoForgeRegistryHelper.Register<?>> {
  @Override
  @SuppressWarnings("unchecked")
  public <R> Register<R> of(ResourceKey<? extends Registry<R>> registryKey, String namespace) {
    return (Register<R>) registries.computeIfAbsent(namespace, key -> new HashMap<>()).computeIfAbsent(registryKey.location(), key -> {
      Register<R> register = Register.create(registryKey, namespace);
      register.register(ModLoader.getBus());
      return register;
    });
  }

  /**
   * Extension for {@link DeferredRegister} to implement {@link CobwebRegister}.
   */
  public static final class Register<R> extends DeferredRegister<R> implements CobwebRegister<R> {
    /**
     * @param registryKey {@link #registryKey}.
     * @param namespace {@link #namespace}.
     */
    private Register(ResourceKey<? extends Registry<R>> registryKey, String namespace) {
      super(registryKey, namespace);
    }

    /**
     * Wrapper around {@link DeferredRegister#create(ResourceKey, String)}.
     *
     * @param registryKey Minecraft {@link Registry} key.
     * @param namespace mod ID.
     * @param <R> register type.
     * @return {@link Register}.
     */
    public static <R> Register<R> create(ResourceKey<? extends Registry<R>> registryKey, String namespace) {
      return new Register<>(registryKey, namespace);
    }
  }
}
