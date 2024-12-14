package it.crystalnest.cobweb.mixin;

import it.crystalnest.cobweb.api.pack.PackSources;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.Set;

/**
 * Injects into {@link PackRepository} to make it possible to add new {@link RepositorySource}s.
 */
@Mixin(PackRepository.class)
public abstract class PackRepositoryMixin implements PackSources {
  /**
   * Shadowed {@link PackRepository#sources}.
   */
  @Final
  @Shadow
  public Set<RepositorySource> sources;

  /**
   * Safe add of new {@link RepositorySource}s.
   *
   * @param source {@link RepositorySource}.
   */
  @Unique
  public synchronized void addSource(RepositorySource source) {
    sources.add(source);
  }
}
