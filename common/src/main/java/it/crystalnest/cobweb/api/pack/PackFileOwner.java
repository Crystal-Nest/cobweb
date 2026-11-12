package it.crystalnest.cobweb.api.pack;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Owns one reference per backing file and releases it on replacement or close.
 */
final class PackFileOwner implements AutoCloseable {
  private final Set<PackFile> files = Collections.newSetFromMap(new IdentityHashMap<>());

  /**
   * Adopts a caller-owned reference without retaining it.
   *
   * @param file backing whose ownership transfers to this owner.
   */
  void adopt(PackFile file) { files.add(file); }

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

  void release(PackFile file) {
    if (files.remove(file)) {
      file.release();
    }
  }

  void keep(Set<PackFile> visible) {
    files.removeIf(file -> {
      if (visible.contains(file)) {
        return false;
      }
      file.release();
      return true;
    });
  }

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
