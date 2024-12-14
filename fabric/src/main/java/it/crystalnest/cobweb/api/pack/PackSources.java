package it.crystalnest.cobweb.api.pack;

import net.minecraft.server.packs.repository.RepositorySource;

/**
 * Container of {@link RepositorySource}s.
 */
public interface PackSources {
  /**
   * Safely add the given source.
   *
   * @param source {@link RepositorySource}.
   */
  void addSource(RepositorySource source);
}
