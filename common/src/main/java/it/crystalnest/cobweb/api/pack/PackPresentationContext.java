package it.crystalnest.cobweb.api.pack;

import net.minecraft.server.packs.PackType;

/**
 * Read-only observations captured when a pack presentation is refreshed.
 *
 * @param pack displayed pack identity.
 * @param type displayed pack type.
 * @param packs current repository and editor observations.
 */
public record PackPresentationContext(PackInfo pack, PackType type, PackQueries packs) {}
