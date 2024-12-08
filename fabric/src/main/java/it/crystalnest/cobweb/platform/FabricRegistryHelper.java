package it.crystalnest.cobweb.platform;

import it.crystalnest.cobweb.api.registry.CobwebEntry;
import it.crystalnest.cobweb.api.registry.CobwebRegister;
import it.crystalnest.cobweb.api.registry.Register;
import it.crystalnest.cobweb.api.registry.RegisterProvider;
import it.crystalnest.cobweb.platform.services.RegistryHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

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
  public <R> DeferredRegister<R> of(ResourceKey<? extends Registry<R>> registryKey, String namespace) {
    return of(id -> new DeferredRegister<>(registryKey, id), registryKey, namespace);
  }

  @Override
  public CobwebRegister.Items ofItems(String namespace) {
    return of(DeferredRegister.Items::new, Registries.ITEM, namespace);
  }

  @Override
  public CobwebRegister.Blocks ofBlocks(String namespace) {
    return of(DeferredRegister.Blocks::new, Registries.BLOCK, namespace);
  }

  /**
   * Provides a {@link DeferredRegister} for the specified mod and {@link Registry}.
   *
   * @param constructor {@link DeferredRegister} subclass constructor.
   * @param registryKey Minecraft {@link Registry} key.
   * @param namespace mod ID.
   * @return {@link DeferredRegister}.
   * @param <R> register type.
   * @param <T> {@link DeferredRegister} type.
   */
  @SuppressWarnings("unchecked")
  private <R, T extends DeferredRegister<R>> T of(Function<String, T> constructor, ResourceKey<? extends Registry<R>> registryKey, String namespace) {
    return (T) registries.computeIfAbsent(namespace, key -> new HashMap<>()).computeIfAbsent(registryKey.location(), key -> constructor.apply(namespace));
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
  public static class DeferredRegister<R> implements CobwebRegister<R> {
    /**
     * {@link Register}.
     */
    private final Register<R> register;

    /**
     * Namespace.
     */
    private final String namespace;

    /**
     * @param registryKey Minecraft {@link Registry} key.
     * @param namespace {@link #namespace}.
     */
    private DeferredRegister(ResourceKey<? extends Registry<R>> registryKey, String namespace) {
      this.register = new RegisterProvider(namespace).of(registryKey);
      this.namespace = namespace;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends R> CobwebEntry<T> register(String name, Supplier<? extends T> supplier) {
      return new CobwebEntry<>(Holder.direct((T) this.register.apply(name, supplier.get())));
    }

    @Override
    public String namespace() {
      return namespace;
    }

    /**
     * Deferred register for {@link Item}s.
     */
    public static final class Items extends DeferredRegister<Item> implements CobwebRegister.Items {
      /**
       * @param namespace {@link #namespace}.
       */
      private Items(String namespace) {
        super(Registries.ITEM, namespace);
      }
    }

    /**
     * Deferred register for {@link Block}s.
     */
    public static final class Blocks extends DeferredRegister<Block> implements CobwebRegister.Blocks {
      /**
       * @param namespace {@link #namespace}.
       */
      private Blocks(String namespace) {
        super(Registries.BLOCK, namespace);
      }
    }
  }
}
