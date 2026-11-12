package it.crystalnest.cobweb.api.pack;

import com.google.gson.JsonObject;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.resources.ResourceMetadata;
import org.jetbrains.annotations.ApiStatus;

import java.util.Optional;

/**
 * Keeps the original JSON alongside native resource metadata decoding.
 *
 * @param delegate native metadata reader.
 * @param json raw sidecar.
 */
@ApiStatus.Internal
public record ResourceMetadataJson(ResourceMetadata delegate, JsonObject json) implements ResourceMetadata {
  @Override
  public <T> Optional<T> getSection(MetadataSectionType<T> type) {
    return delegate.getSection(type);
  }
}
