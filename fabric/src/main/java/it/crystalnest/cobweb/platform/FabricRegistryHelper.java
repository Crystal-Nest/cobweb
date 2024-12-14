package it.crystalnest.cobweb.platform;

import it.crystalnest.cobweb.api.pack.PackSources;
import it.crystalnest.cobweb.api.pack.fixed.StaticResourcePack;
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
import net.minecraft.server.packs.repository.PackRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;
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
   * Internal list of {@link StaticResourcePack} of type {@link PackType#SERVER_DATA}.
   */
  private static final List<StaticResourcePack> STATIC_DATA_PACKS = new ArrayList<>();

  /**
   * Internal list of {@link StaticResourcePack} of type {@link PackType#CLIENT_RESOURCES}.
   */
  private static final List<StaticResourcePack> STATIC_TEXTURE_PACKS = new ArrayList<>();

  /**
   * Registers all data packs, dynamic and static, to the given repository.
   *
   * @param repository {@link PackRepository}.
   * @return updated repository.
   */
  public static PackRepository registerDataPacks(PackRepository repository) {
    registerDynamicPacks(DYNAMIC_DATA_PACKS, repository);
    registerStaticPacks(STATIC_DATA_PACKS, repository);
    return repository;
  }

  /**
   * Registers all texture packs, dynamic and static, to the given repository.
   *
   * @param repository {@link PackRepository}.
   * @return updated repository.
   */
  public static PackRepository registerTexturePacks(PackRepository repository) {
    registerDynamicPacks(DYNAMIC_TEXTURE_PACKS, repository);
    registerStaticPacks(STATIC_TEXTURE_PACKS, repository);
    return repository;
  }

  /**
   * Registers the provided list of dynamic resource packs to the given repository.
   *
   * @param packs list of dynamic packs.
   * @param repository {@link PackRepository} to update.
   */
  private static void registerDynamicPacks(List<Supplier<Pack>> packs, PackRepository repository) {
    registerPacks(packs, Supplier::get, repository);
  }

  /**
   * Registers the provided list of static resource packs to the given repository.
   *
   * @param packs list of static packs.
   * @param repository {@link PackRepository} to update.
   */
  private static void registerStaticPacks(List<StaticResourcePack> packs, PackRepository repository) {
    registerPacks(packs, StaticResourcePack::toPack, repository);
  }

  /**
   * Registers the provided list of resource packs to the given repository.
   *
   * @param packs resource packs.
   * @param toPack function to map a resource pack into a {@link Pack}.
   * @param repository {@link PackRepository}.
   * @param <T> resource pack type.
   */
  private static <T> void registerPacks(List<T> packs, Function<T, Pack> toPack, PackRepository repository) {
    packs.stream().map(toPack).forEach(pack -> ((PackSources) repository).addSource(packConsumer -> packConsumer.accept(pack)));
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

  @Override
  public void registerStaticResourcePack(StaticResourcePack pack) {
    switch (pack.type()) {
      case SERVER_DATA -> STATIC_DATA_PACKS.add(pack);
      case CLIENT_RESOURCES -> STATIC_TEXTURE_PACKS.add(pack);
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
