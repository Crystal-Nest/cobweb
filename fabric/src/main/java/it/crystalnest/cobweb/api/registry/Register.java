package it.crystalnest.cobweb.api.registry;

import net.minecraft.core.Registry;

import java.util.function.BiFunction;

/**
 * Register provided by {@link RegisterProvider} for a specified Minecraft {@link Registry}.
 *
 * @param <T> registry type.
 */
@FunctionalInterface
public interface Register<T> extends BiFunction<String, T, T> {}
