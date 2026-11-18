package it.crystalnest.cobweb.api.pack.generation;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Describes a generated operation independently of execution and persistence.
 * Each operation transfers its backing references to its accepting owner.
 */
sealed interface PackContribution {
  /**
   * Returns the destination identifier.
   *
   * @return namespaced file identifier.
   */
  Identifier id();

  /**
   * Returns the file backing references transferred with this contribution.
   *
   * @return the backing files, or an empty list for an operation with no backing.
   */
  List<PackFile> backing();

  /**
   * Acquires another lease on each backing file.
   */
  default void retain() {
    backing().forEach(PackFile::retain);
  }

  /**
   * Releases one lease on each backing file.
   */
  default void release() {
    backing().forEach(PackFile::release);
  }

  /**
   * Estimates the size charged to a cache batch.
   *
   * @return size in bytes, including estimates for in-memory contributions.
   */
  default long bytes() {
    return backing().stream().mapToLong(PackFile::size).sum();
  }

  /**
   * Creates a complete file replacement.
   *
   * @param id destination file identifier.
   * @param contents owned backing to transfer.
   * @return file replacement contribution.
   */
  static PackContribution file(Identifier id, PackFile contents) {
    return new File(id, contents);
  }

  /**
   * Creates an explicit addition to a tag.
   *
   * @param id destination tag file identifier.
   * @param addition entries to append.
   * @return tag addition contribution.
   */
  static PackContribution tag(Identifier id, GenerationOutput.TagAddition addition) {
    return new Tag(id, addition);
  }

  /**
   * Creates a grouped replacement for a file and its companion sidecar.
   *
   * @param id destination file identifier.
   * @param contents owned main file backing.
   * @param metadata owned sidecar backing, or {@code null} to clear an earlier grouped sidecar.
   * @return grouped file contribution.
   */
  static PackContribution withMetadata(Identifier id, PackFile contents, @Nullable PackFile metadata) {
    return new WithMetadata(id, contents, metadata);
  }

  /**
   * Creates a replacement for one named member in a JSON object.
   *
   * @param id destination JSON file identifier.
   * @param name property name.
   * @param contents owned backing of the complete member value.
   * @return member contribution.
   */
  static PackContribution member(Identifier id, String name, PackFile contents) {
    return new Member(id, name, contents);
  }

  /**
   * Replaces one complete pack file.
   *
   * @param id destination file identifier.
   * @param contents complete file backing.
   */
  record File(Identifier id, PackFile contents) implements PackContribution {
    /**
     * @param id destination file identifier.
     * @param contents complete file backing.
     */
    public File {
      Objects.requireNonNull(id);
      Objects.requireNonNull(contents);
    }

    @Override
    public List<PackFile> backing() {
      return List.of(contents);
    }
  }

  /**
   * Appends entries to an existing or new tag file.
   *
   * @param id destination tag file identifier.
   * @param addition entries to append.
   */
  record Tag(Identifier id, GenerationOutput.TagAddition addition) implements PackContribution {
    /**
     * @param id destination tag file identifier.
     * @param addition entries to append.
     */
    public Tag {
      Objects.requireNonNull(id);
      Objects.requireNonNull(addition);
    }

    @Override
    public List<PackFile> backing() {
      return List.of();
    }

    @Override
    public long bytes() {
      return 256L + 96L * addition.entries().size();
    }
  }

  /**
   * Replaces a file and its grouped sidecar.
   * Absent metadata clears an earlier grouped sidecar.
   *
   * @param id destination file identifier.
   * @param contents complete file backing.
   * @param metadata companion backing, or {@code null} to clear an earlier grouped sidecar.
   */
  record WithMetadata(Identifier id, PackFile contents, @Nullable PackFile metadata) implements PackContribution {
    /**
     * @param id destination file identifier.
     * @param contents complete file backing.
     * @param metadata companion backing, or {@code null} to clear an earlier grouped sidecar.
     */
    public WithMetadata {
      Objects.requireNonNull(id);
      Objects.requireNonNull(contents);
    }

    @Override
    public List<PackFile> backing() {
      return metadata == null ? List.of(contents) : List.of(contents, metadata);
    }

    /**
     * Resolves the native sidecar name for this contribution.
     *
     * @return destination file identifier with the {@code .mcmeta} suffix.
     */
    Identifier metadataId() {
      return id.withPath(path -> path + ".mcmeta");
    }
  }

  /**
   * Replaces one named member in an aggregate JSON file.
   *
   * @param id destination JSON file identifier.
   * @param name member name.
   * @param contents backing of the complete member value.
   */
  record Member(Identifier id, String name, PackFile contents) implements PackContribution {
    /**
     * @param id destination JSON file identifier.
     * @param name member name.
     * @param contents backing of the complete member value.
     */
    public Member {
      Objects.requireNonNull(id);
      Objects.requireNonNull(name);
      Objects.requireNonNull(contents);
    }

    @Override
    public List<PackFile> backing() {
      return List.of(contents);
    }
  }
}
