package it.crystalnest.cobweb.api.pack.generation;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Owns one reference per backing file and releases it on replacement or close.
 */
final class PackFileOwner implements AutoCloseable {
  /**
   * The identity-keyed reference counts held by this owner.
   */
  private final Set<PackFile> files = Collections.newSetFromMap(new IdentityHashMap<>());

  /**
   * Adopts a caller-owned reference without retaining it.
   *
   * @param file backing whose ownership transfers to this owner.
   */
  void adopt(PackFile file) {
    files.add(file);
  }

  /**
   * Retains an independently owned reference when not already held here.
   *
   * @param file shared backing.
   */
  void retain(PackFile file) {
    if (files.add(file)) {
      file.retain();
    }
  }

  /**
   * Releases one reference to the file if this owner holds one.
   *
   * @param file file whose lease should be released.
   */
  void release(PackFile file) {
    if (files.remove(file)) {
      file.release();
    }
  }

  /**
   * Releases every owned reference to files outside the retained set.
   *
   * @param visible files whose owned references should be kept.
   */
  void keep(Set<PackFile> visible) {
    files.removeIf(file -> {
      if (!visible.contains(file)) {
        file.release();
        return true;
      }
      return false;
    });
  }

  /**
   * Transfers this owner's outstanding leases into another owner.
   *
   * @param target destination owner of the transferred leases.
   */
  void transferTo(PackFileOwner target) {
    files.forEach(target::adopt);
    files.clear();
  }

  @Override
  public void close() {
    files.forEach(PackFile::release);
    files.clear();
  }
}
