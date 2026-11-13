package it.crystalnest.cobweb.api.pack.generation;

import it.crystalnest.cobweb.Constants;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * Owns the immutable bytes of one submitted file and the leases of its native readers.
 */
final class PackFile {
  private static final int MEMORY_PER_FILE = 256 * 1024;

  private static final long MEMORY_LIMIT = 32L * 1024 * 1024;

  private static final long SCRATCH_LIMIT = 4L * 1024 * 1024 * 1024;

  private static final AtomicLong MEMORY = new AtomicLong();

  private static final AtomicLong SCRATCH = new AtomicLong();

  private static final Pattern SESSION_DIRECTORY = Pattern.compile("session-([0-9]+)-[0-9]+");

  private static Path scratchDirectory;

  private final byte[] bytes;

  private final Path path;

  private final long size;

  private int leases = 1;

  private String digest;

  private PackFile(byte[] bytes, Path path, long size) {
    this.bytes = bytes;
    this.path = path;
    this.size = size;
  }

  long size() {
    return size;
  }

  static PackFile write(GenerationOutput.FileWriter writer) throws IOException {
    try (var spool = new Spool()) {
      writer.write(new FilterOutputStream(spool) {
        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
          out.write(bytes, offset, length);
        }

        @Override
        public void close() {}
      });
      return spool.finish();
    }
  }

  synchronized void retain() {
    if (leases == 0) {
      throw new IllegalStateException("Pack file has already been released");
    }
    leases++;
  }

  synchronized InputStream open() throws IOException {
    if (leases == 0) {
      throw new IOException("Pack file has already been released");
    }
    if (size > PackIO.captureLimit()) {
      throw new IOException("Resource exceeds " + PackIO.captureLimit() + " bytes");
    }
    InputStream input = path == null ? new ByteArrayInputStream(bytes) : Files.newInputStream(path);
    leases++;
    return new FileStream(input);
  }

  private synchronized String digest() throws IOException {
    if (digest == null) {
      var hash = CacheObservations.digest();
      try (var input = path == null ? new ByteArrayInputStream(bytes) : Files.newInputStream(path)) {
        input.transferTo(new java.security.DigestOutputStream(OutputStream.nullOutputStream(), hash));
      }
      digest = CacheObservations.hex(hash);
    }
    return digest;
  }

  interface CapturedStream {
    String digest() throws IOException;

    long size() throws IOException;
  }

  private final class FileStream extends FilterInputStream implements CapturedStream {
    private boolean closed;

    private FileStream(InputStream input) { super(input); }

    @Override
    public String digest() throws IOException { return PackFile.this.digest(); }

    @Override
    public long size() { return size; }

    @Override
    public void close() throws IOException {
      if (!closed) {
        closed = true;
        try {
          super.close();
        } finally {
          release();
        }
      }
    }
  }

  synchronized void release() {
    if (--leases == 0) {
      if (path == null) {
        MEMORY.addAndGet(-size);
      } else {
        delete(path);
        SCRATCH.addAndGet(-size);
      }
    }
  }

  private static boolean reserve(AtomicLong budget, long size, long limit) {
    long current;
    do {
      current = budget.get();
      if (size > limit - current) {
        return false;
      }
    } while (!budget.compareAndSet(current, current + size));
    return true;
  }

  private static void delete(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException failure) {
      Constants.LOGGER.warn("Cannot remove Cobweb scratch file {}", path, failure);
    }
  }

  private static synchronized Path scratchDirectory() throws IOException {
    if (scratchDirectory == null) {
      var root = Files.createDirectories(Path.of("cobweb", "scratch").toAbsolutePath().normalize());
      scratchDirectory = Files.createTempDirectory(root, "session-" + ProcessHandle.current().pid() + "-");
      Thread.ofVirtual().name("Cobweb scratch cleanup").start(() -> cleanAbandoned(root));
    }
    return scratchDirectory;
  }

  private static void cleanAbandoned(Path root) {
    try (var paths = Files.list(root)) {
      for (var path : paths.toList()) {
        var name = SESSION_DIRECTORY.matcher(path.getFileName().toString());
        if (!path.normalize().startsWith(root) || !name.matches() || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
          continue;
        }
        if (ProcessHandle.of(Long.parseLong(name.group(1))).filter(ProcessHandle::isAlive).isPresent()) {
          continue;
        }
        try (var files = Files.newDirectoryStream(path, "resource-*.bin")) {
          for (var file : files) {
            if (file.normalize().startsWith(path) && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
              delete(file);
            }
          }
        }
        delete(path);
      }
    } catch (IOException | RuntimeException failure) {
      Constants.LOGGER.warn("Unable to clean abandoned pack scratch storage under {}", root, failure);
    }
  }

  private static final class Spool extends OutputStream {
    private ByteArrayOutputStream memory = new ByteArrayOutputStream();

    private OutputStream disk;

    private Path path;

    private long size;

    private long reservedDisk;

    private boolean finished;

    @Override
    public void write(int value) throws IOException {
      if (finished) {
        throw new IOException("Pack file writer has ended");
      }
      if (disk == null && size == MEMORY_PER_FILE) {
        spill();
      }
      if (disk == null) {
        memory.write(value);
      } else {
        reserveDisk(1);
        disk.write(value);
      }
      size++;
    }

    @Override
    public void write(byte[] source, int offset, int length) throws IOException {
      java.util.Objects.checkFromIndexSize(offset, length, source.length);
      if (finished) {
        throw new IOException("Pack file writer has ended");
      }
      if (disk == null && size + length > MEMORY_PER_FILE) {
        spill();
      }
      if (disk == null) {
        memory.write(source, offset, length);
      } else {
        reserveDisk(length);
        disk.write(source, offset, length);
      }
      size += length;
    }

    private void spill() throws IOException {
      reserveDisk(size);
      var directory = scratchDirectory();
      path = Files.createTempFile(directory, "resource-", ".bin");
      disk = new java.io.BufferedOutputStream(Files.newOutputStream(path));
      memory.writeTo(disk);
      memory = null;
    }

    private void reserveDisk(long length) throws IOException {
      if (!reserve(SCRATCH, length, SCRATCH_LIMIT)) {
        throw new IOException("Cobweb resource scratch limit reached");
      }
      reservedDisk += length;
    }

    PackFile finish() throws IOException {
      if (disk == null && reserve(MEMORY, size, MEMORY_LIMIT)) {
        finished = true;
        return new PackFile(memory.toByteArray(), null, size);
      }
      if (disk == null) {
        spill();
      }
      disk.close();
      disk = null;
      finished = true;
      return new PackFile(null, path, size);
    }

    @Override
    public void close() throws IOException {
      try {
        if (disk != null) {
          disk.close();
        }
      } finally {
        if (!finished) {
          SCRATCH.addAndGet(-reservedDisk);
          if (path != null) {
            delete(path);
          }
        }
      }
    }
  }
}
