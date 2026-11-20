package it.crystalnest.cobweb.api.pack.generation;

import com.google.gson.JsonElement;
import net.minecraft.server.packs.resources.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Encodes shared file contents and bounds reads before they enter temporary storage.
 */
final class PackIO {
  /**
   * Optional byte limit propagated through native stream wrappers for a bounded read.
   */
  private static final ScopedValue<Long> CAPTURE_LIMIT = ScopedValue.newInstance();

  private PackIO() {}

  /**
   * Carries the limit through native stream decorators to the backing supplier.
   *
   * @param resource resolved native resource.
   * @param maxBytes maximum captured bytes.
   * @return native stream, including any leak-detection wrapper.
   * @throws IOException if opening or bounded capture fails.
   */
  static InputStream open(Resource resource, long maxBytes) throws IOException {
    return ScopedValue.where(CAPTURE_LIMIT, Math.min(captureLimit(), maxBytes)).call(resource::open);
  }

  /**
   * Returns the limit applied to the current synchronous read.
   *
   * @return byte limit, or {@link Long#MAX_VALUE} for an unbounded read.
   */
  static long captureLimit() {
    return CAPTURE_LIMIT.orElse(Long.MAX_VALUE);
  }

  /**
   * Checks a captured stream against the read's maximum size.
   *
   * @param input stream whose captured size is checked.
   * @param maxBytes maximum permitted number of bytes.
   * @throws IOException if the captured resource exceeds the limit or its size cannot be read.
   */
  static void checkLimit(InputStream input, long maxBytes) throws IOException {
    if (input instanceof StageResourceSnapshots.SnapshotStream snapshot) {
      snapshot.initialize(maxBytes);
    } else if (input instanceof PackFile.CapturedStream captured && captured.size() > maxBytes) {
      throw new IOException(resourceLimitMessage(maxBytes));
    }
  }

  /**
   * Serializes a JSON value as UTF-8.
   *
   * @param value value to encode.
   * @return encoded JSON bytes.
   */
  static byte[] json(JsonElement value) {
    return value.toString().getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Copies complete contents while enforcing the maximum byte count.
   *
   * @param input source stream.
   * @param output destination stream.
   * @param limit maximum number of bytes to copy.
   * @throws IOException if copying fails or the limit is exceeded.
   */
  static void copy(InputStream input, OutputStream output, long limit) throws IOException {
    byte[] buffer = new byte[8192];
    long remaining = limit;
    int count;
    while ((count = input.read(buffer, 0, (int) Math.min(buffer.length, remaining == Long.MAX_VALUE ? remaining : remaining + 1))) >= 0) {
      if (count > remaining) {
        throw new IOException(resourceLimitMessage(limit));
      }
      output.write(buffer, 0, count);
      remaining -= count;
    }
  }
  /**
   * Formats the shared bounded-resource failure message.
   *
   * @param maximum maximum permitted byte count.
   * @return diagnostic containing the byte limit.
   */
  static String resourceLimitMessage(long maximum) {
    return "Resource exceeds " + maximum + " bytes";
  }
}
