package it.crystalnest.cobweb.platform;

import it.crystalnest.cobweb.api.registry.CobwebRegister;
import it.crystalnest.cobweb.api.registry.Register;
import it.crystalnest.cobweb.api.registry.RegisterProvider;
import it.crystalnest.cobweb.platform.services.RegistryHelper;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

import java.util.HashMap;
import java.util.function.Supplier;

/**
 * Fabric registration helper.
 */
public final class FabricRegistryHelper extends RegistryHelper<FabricRegistryHelper.DeferredRegister<?>> {
  @Override
  @SuppressWarnings("unchecked")
  public <R> DeferredRegister<R> of(ResourceKey<? extends Registry<R>> registryKey, String namespace) {
    return (DeferredRegister<R>) registries.computeIfAbsent(namespace, key -> new HashMap<>()).computeIfAbsent(registryKey.location(), key -> new DeferredRegister<>(new RegisterProvider(namespace).of(registryKey)));
  }

  /**
   * Deferred register.
   */
  public static final class DeferredRegister<R> implements CobwebRegister<R> {
    /**
     * {@link Register}.
     */
    private final Register<R> register;

    /**
     * @param register {@link #register}.
     */
    private DeferredRegister(Register<R> register) {
      this.register = register;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends R> Supplier<T> register(String name, Supplier<? extends T> supplier) {
      T value = (T) this.register.apply(name, supplier.get());
      return () -> value;
    }
  }
}
