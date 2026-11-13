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
  Identifier id();

  List<PackFile> backing();

  default void retain() {
    backing().forEach(PackFile::retain);
  }

  default void release() {
    backing().forEach(PackFile::release);
  }

  default long bytes() {
    return backing().stream().mapToLong(PackFile::size).sum();
  }

  static PackContribution file(Identifier id, PackFile contents) {
    return new File(id, contents);
  }

  static PackContribution tag(Identifier id, GenerationOutput.TagAddition addition) {
    return new Tag(id, addition);
  }

  static PackContribution withMetadata(Identifier id, PackFile contents, @Nullable PackFile metadata) {
    return new WithMetadata(id, contents, metadata);
  }

  static PackContribution member(Identifier id, String name, PackFile contents) {
    return new Member(id, name, contents);
  }

  record File(Identifier id, PackFile contents) implements PackContribution {
    public File {
      Objects.requireNonNull(id);
      Objects.requireNonNull(contents);
    }

    @Override
    public List<PackFile> backing() { return List.of(contents); }
  }

  record Tag(Identifier id, GenerationOutput.TagAddition addition) implements PackContribution {
    public Tag {
      Objects.requireNonNull(id);
      Objects.requireNonNull(addition);
    }

    @Override
    public List<PackFile> backing() { return List.of(); }

    @Override
    public long bytes() { return 256L + 96L * addition.entries().size(); }
  }

  /**
   * Replaces a file and its grouped sidecar; absent metadata clears an earlier grouped sidecar only.
   */
  record WithMetadata(Identifier id, PackFile contents, @Nullable PackFile metadata) implements PackContribution {
    public WithMetadata {
      Objects.requireNonNull(id);
      Objects.requireNonNull(contents);
    }

    @Override
    public List<PackFile> backing() { return metadata == null ? List.of(contents) : List.of(contents, metadata); }

    Identifier metadataId() { return id.withPath(path -> path + ".mcmeta"); }
  }

  record Member(Identifier id, String name, PackFile contents) implements PackContribution {
    public Member {
      Objects.requireNonNull(id);
      Objects.requireNonNull(name);
      Objects.requireNonNull(contents);
    }

    @Override
    public List<PackFile> backing() { return List.of(contents); }
  }
}
