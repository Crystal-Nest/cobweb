package it.crystalnest.cobweb.api.pack.presentation;

import it.crystalnest.cobweb.api.pack.query.PackInfo;
import it.crystalnest.cobweb.api.pack.query.PackQueries;
import net.minecraft.server.packs.PackType;

/**
 * Read-only observations captured when a pack presentation is refreshed.
 *
 * @param pack displayed pack identity.
 * @param type displayed pack type.
 * @param packs current repository and editor observations.
 */
public record PackPresentationContext(PackInfo pack, PackType type, PackQueries packs) {}
