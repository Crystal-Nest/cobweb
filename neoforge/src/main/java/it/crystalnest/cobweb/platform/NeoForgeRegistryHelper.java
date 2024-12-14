package it.crystalnest.cobweb.platform;

import it.crystalnest.cobweb.api.pack.fixed.StaticResourcePack;
import it.crystalnest.cobweb.api.registry.CobwebEntry;
import it.crystalnest.cobweb.api.registry.CobwebRegister;
import it.crystalnest.cobweb.platform.services.RegistryHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.HashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * NeoForge registration helper.
 */
public final class NeoForgeRegistryHelper extends RegistryHelper<NeoForgeRegistryHelper.Register<?>> {
  /**
   * NeoForge event bus.
   */
  private IEventBus bus = null;

  @Override
  @SuppressWarnings("unchecked")
  public <R> Register<R> of(ResourceKey<? extends Registry<R>> registryKey, String namespace) {
    return (Register<R>) registries.computeIfAbsent(namespace, key -> new HashMap<>()).computeIfAbsent(registryKey.location(), key -> {
      Register<R> register = Register.create(registryKey, namespace);
      register.register(bus);
      return register;
    });
  }

  @Override
  public void registerDynamicResourcePack(PackType type, Supplier<Pack> supplier) {
    bus.addListener((AddPackFindersEvent event) -> {
      if (event.getPackType() == type) {
        event.addRepositorySource(consumer -> consumer.accept(supplier.get()));
      }
    });
  }

  @Override
  public void registerStaticResourcePack(StaticResourcePack pack) {
    bus.addListener((AddPackFindersEvent event) -> event.addPackFinders(pack.location(), pack.type(), pack.name(), pack.source(), pack.alwaysActive(), pack.position()));
  }

  /**
   * Provides a {@link Register} for the specified mod and {@link Registry}.
   *
   * @param constructor {@link Register} subclass constructor.
   * @param registryKey Minecraft {@link Registry} key.
   * @param namespace mod ID.
   * @param <R> register type.
   * @param <T> {@link Register} type.
   * @return {@link Register}.
   */
  @SuppressWarnings("unchecked")
  private <R, T extends Register<R>> T of(Function<String, T> constructor, ResourceKey<? extends Registry<R>> registryKey, String namespace) {
    return (T) registries.computeIfAbsent(namespace, key -> new HashMap<>()).computeIfAbsent(registryKey.location(), key -> {
      T register = constructor.apply(namespace);
      register.register(bus);
      return register;
    });
  }

  /**
   * Saves the given event bus to register game objects.
   *
   * @param bus {@link IEventBus}.
   */
  public void register(IEventBus bus) {
    this.bus = bus;
  }

  /**
   * Extension for {@link DeferredRegister} to implement {@link CobwebRegister}.
   *
   * @param <R> registry type.
   */
  public static final class Register<R> implements CobwebRegister<R> {
    /**
     * Wrapped instance of {@link DeferredRegister}.
     */
    private final DeferredRegister<R> register;

    /**
     * @param registryKey {@link DeferredRegister#registryKey}.
     * @param namespace {@link DeferredRegister#namespace}.
     */
    private Register(ResourceKey<? extends Registry<R>> registryKey, String namespace) {
      register = DeferredRegister.create(registryKey, namespace);
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

    /**
     * Wrapper around {@link DeferredRegister#register(IEventBus)}.
     *
     * @param bus {@link IEventBus}.
     */
    public void register(IEventBus bus) {
      register.register(bus);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends R> CobwebEntry<T> register(String name, Supplier<? extends T> supplier) {
      return new CobwebEntry<>((Holder<T>) register.register(name, supplier));
    }
  }
}
