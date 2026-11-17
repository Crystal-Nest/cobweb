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
  /**
   * Maximum size for retaining one file entirely in memory.
   */
  private static final int MEMORY_PER_FILE = 256 * 1024;

  /**
   * Total budget for file backing retained in memory.
   */
  private static final long MEMORY_LIMIT = 32L * 1024 * 1024;

  /**
   * Total budget for file backing in the scratch directory.
   */
  private static final long SCRATCH_LIMIT = 4L * 1024 * 1024 * 1024;

  /**
   * Bytes currently reserved by in-memory backing.
   */
  private static final AtomicLong MEMORY = new AtomicLong();

  /**
   * Bytes currently reserved by scratch backing.
   */
  private static final AtomicLong SCRATCH = new AtomicLong();

  /**
   * Pattern used to identify process-specific scratch directories.
   */
  private static final Pattern SESSION_DIRECTORY = Pattern.compile("session-([0-9]+)-[0-9]+");

  /**
   * Private scratch directory created lazily on the first spill.
   */
  private static Path scratchDirectory;

  /**
   * In-memory bytes, or {@code null} when this file spilled to disk.
   */
  private final byte[] bytes;

  /**
   * Scratch path, or {@code null} when this file is held in memory.
   */
  private final Path path;

  /**
   * Length of this file's encoded contents in bytes.
   */
  private final long size;

  /**
   * Number of outstanding owners and open streams.
   */
  private int leases = 1;

  /**
   * Lazily computed SHA-256 fingerprint of the file contents.
   */
  private String digest;

  /**
   * @param bytes in-memory bytes, or {@code null} for a scratch file.
   * @param path scratch path, or {@code null} for in-memory bytes.
   * @param size file length in bytes.
   */
  private PackFile(byte[] bytes, Path path, long size) {
    this.bytes = bytes;
    this.path = path;
    this.size = size;
  }

  /**
   * Returns the recorded size of this file.
   *
   * @return file size in bytes.
   */
  long size() {
    return size;
  }

  /**
   * Spools the complete result of a synchronous writer and disposes of partial output on failure.
   *
   * @param writer writer of the complete file.
   * @return new backing with one owned lease.
   * @throws IOException if the underlying I/O operation fails.
   */
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

  /**
   * Acquires another lease on this backing.
   *
   * @throws IllegalStateException if the last lease was already released.
   */
  synchronized void retain() {
    if (leases == 0) {
      throw new IllegalStateException("Pack file has already been released");
    }
    leases++;
  }

  /**
   * Opens a stream that holds its own backing lease until it is closed.
   *
   * @return independently leased stream.
   * @throws IOException if the underlying I/O operation fails.
   */
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

  /**
   * Returns the SHA-256 fingerprint of the immutable file bytes.
   *
   * @return hexadecimal digest of the file.
   * @throws IOException if the underlying I/O operation fails.
   */
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

  /**
   * Exposes stable content information for a stream backed by an immutable capture.
   */
  interface CapturedStream {
    /**
     * Returns the SHA-256 fingerprint of the immutable file bytes.
     *
     * @return hexadecimal digest of the file.
     * @throws IOException if the underlying I/O operation fails.
     */
    String digest() throws IOException;

    /**
     * Returns the recorded size of this file.
     *
     * @return file size in bytes.
     * @throws IOException if the underlying I/O operation fails.
     */
    long size() throws IOException;
  }

  /**
   * Holds a backing lease for the lifetime of an opened file stream.
   */
  private final class FileStream extends FilterInputStream implements CapturedStream {
    /**
     * Whether the stream has released its backing lease.
     */
    private boolean closed;

    /**
     * @param input input stream backed by this file.
     */
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

  /**
   * Releases one lease and frees the backing when the last lease ends.
   */
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

  /**
   * Tries to reserve bytes without exceeding the shared budget.
   *
   * @param budget budget counter.
   * @param size number of bytes to reserve.
   * @param limit maximum total budget.
   * @return whether the reservation succeeded.
   */
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

  /**
   * Attempts to remove a scratch file and reports an I/O failure.
   *
   * @param path path of the scratch file.
   */
  private static void delete(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException failure) {
      Constants.LOGGER.error("Cannot remove Cobweb scratch file {}", path, failure);
    }
  }

  /**
   * Creates or retrieves this process's scratch directory.
   *
   * @return scratch directory for the current session.
   * @throws IOException if the underlying I/O operation fails.
   */
  private static synchronized Path scratchDirectory() throws IOException {
    if (scratchDirectory == null) {
      var root = Files.createDirectories(Path.of("cobweb", "scratch").toAbsolutePath().normalize());
      scratchDirectory = Files.createTempDirectory(root, "session-" + ProcessHandle.current().pid() + "-");
      Thread.ofVirtual().name("Cobweb scratch cleanup").start(() -> cleanAbandoned(root));
    }
    return scratchDirectory;
  }

  /**
   * Removes scratch files from expired sessions when possible.
   *
   * @param root parent of the per-session scratch directories.
   */
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
      Constants.LOGGER.error("Unable to clean abandoned pack scratch storage under {}", root, failure);
    }
  }

  /**
   * Collects a complete file in memory and spills to disk when required by the budgets.
   */
  private static final class Spool extends OutputStream {
    /**
     * In-memory buffer retained until the output spills.
     */
    private ByteArrayOutputStream memory = new ByteArrayOutputStream();

    /**
     * Current scratch output stream once the memory buffer has spilled.
     */
    private OutputStream disk;

    /**
     * Scratch path, or {@code null} when this file is held in memory.
     */
    private Path path;

    /**
     * Length of this file's encoded contents in bytes.
     */
    private long size;

    /**
     * Number of bytes reserved in the shared scratch budget for this output.
     */
    private long reservedDisk;

    /**
     * Whether the spool has handed off its backing to a complete file.
     */
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

    /**
     * Creates scratch backing and copies the already buffered bytes into it.
     *
     * @throws IOException if the underlying I/O operation fails.
     */
    private void spill() throws IOException {
      reserveDisk(size);
      var directory = scratchDirectory();
      path = Files.createTempFile(directory, "resource-", ".bin");
      disk = new java.io.BufferedOutputStream(Files.newOutputStream(path));
      memory.writeTo(disk);
      memory = null;
    }

    /**
     * Reserves additional scratch capacity for this file.
     *
     * @param length number of bytes to reserve.
     * @throws IOException if the underlying I/O operation fails.
     */
    private void reserveDisk(long length) throws IOException {
      if (!reserve(SCRATCH, length, SCRATCH_LIMIT)) {
        throw new IOException("Cobweb resource scratch limit reached");
      }
      reservedDisk += length;
    }

    /**
     * Closes the output and transfers its backing to a complete file.
     *
     * @return new file with ownership of its backing.
     * @throws IOException if the underlying I/O operation fails.
     */
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
