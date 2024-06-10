package it.crystalnest.cobweb.platform;

import it.crystalnest.cobweb.api.registry.CobwebRegister;
import it.crystalnest.cobweb.platform.services.RegistryHelper;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;

import java.util.HashMap;
import java.util.function.Supplier;

/**
 * Forge registration helper.
 */
public final class ForgeRegistryHelper extends RegistryHelper<ForgeRegistryHelper.Register<?>> {
  @Override
  @SuppressWarnings("unchecked")
  public <R> Register<R> of(ResourceKey<? extends Registry<R>> registryKey, String namespace) {
    return (Register<R>) registries.computeIfAbsent(namespace, key -> new HashMap<>()).computeIfAbsent(registryKey.location(), key -> {
      Register<R> register = new Register<>(DeferredRegister.create(registryKey, namespace));
      register.register(FMLJavaModLoadingContext.get().getModEventBus());
      return register;
    });
  }

  @Override
  public void registerDynamicResourcePack(PackType type, Supplier<Pack> supplier) {
    FMLJavaModLoadingContext.get().getModEventBus().addListener((AddPackFindersEvent event) -> {
      if (event.getPackType() == type) {
        event.addRepositorySource(consumer -> consumer.accept(supplier.get()));
      }
    });
  }

  /**
   * Wrapper around {@link DeferredRegister}.
   */
  public static final class Register<R> implements CobwebRegister<R> {
    /**
     * {@link DeferredRegister} instance.
     */
    private final DeferredRegister<R> register;

    /**
     * @param register {@link #register}.
     */
    private Register(DeferredRegister<R> register) {
      this.register = register;
    }

    /**
     * Wrapper around {@link DeferredRegister#register(IEventBus)}.
     *
     * @param bus event bus.
     */
    public void register(IEventBus bus) {
      register.register(bus);
    }

    @Override
    public <T extends R> Supplier<T> register(String name, Supplier<? extends T> supplier) {
      return register.register(name, supplier);
    }
  }
}
