package it.crystalnest.cobweb.platform;

import it.crystalnest.cobweb.api.registry.CobwebEntry;
import it.crystalnest.cobweb.api.registry.CobwebRegister;
import it.crystalnest.cobweb.api.registry.Register;
import it.crystalnest.cobweb.api.registry.RegisterProvider;
import it.crystalnest.cobweb.platform.services.RegistryHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Supplier;

/**
 * Fabric registration helper.
 */
public final class FabricRegistryHelper extends RegistryHelper<FabricRegistryHelper.DeferredRegister<?>> {
  /**
   * Internal list of {@link Pack data pack}s to register.
   */
  private static final List<Supplier<Pack>> DYNAMIC_DATA_PACKS = new ArrayList<>();

  /**
   * Internal list of {@link Pack texture pack}s to register.
   */
  private static final List<Supplier<Pack>> DYNAMIC_TEXTURE_PACKS = new ArrayList<>();

  /**
   * Returns the list of currently registered dynamic {@link Pack data pack}s.
   *
   * @return list of registered dynamic {@link Pack data pack}s.
   */
  public static List<Pack> dynamicDataPacks() {
    return DYNAMIC_DATA_PACKS.stream().map(Supplier::get).toList();
  }

  /**
   * Returns the list of currently registered dynamic {@link Pack texture pack}s.
   *
   * @return list of registered dynamic {@link Pack texture pack}s.
   */
  public static List<Pack> dynamicTexturePacks() {
    return DYNAMIC_TEXTURE_PACKS.stream().map(Supplier::get).toList();
  }

  @Override
  @SuppressWarnings("unchecked")
  public <R> DeferredRegister<R> of(ResourceKey<? extends Registry<R>> registryKey, String namespace) {
    return (DeferredRegister<R>) registries.computeIfAbsent(namespace, key -> new HashMap<>()).computeIfAbsent(registryKey.location(), key -> new DeferredRegister<>(new RegisterProvider(namespace).of(registryKey)));
  }

  @Override
  public void registerDynamicResourcePack(PackType type, Supplier<Pack> supplier) {
    switch (type) {
      case SERVER_DATA -> DYNAMIC_DATA_PACKS.add(supplier);
      case CLIENT_RESOURCES -> DYNAMIC_TEXTURE_PACKS.add(supplier);
    }
  }

  /**
   * Deferred register.
   *
   * @param <R> registry type.
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
    public <T extends R> CobwebEntry<T> register(String name, Supplier<? extends T> supplier) {
      return new CobwebEntry<>(Holder.direct((T) this.register.apply(name, supplier.get())));
    }
  }
}
