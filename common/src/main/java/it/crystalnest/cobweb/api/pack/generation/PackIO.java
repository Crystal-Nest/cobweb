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

  static long captureLimit() {
    return CAPTURE_LIMIT.orElse(Long.MAX_VALUE);
  }

  static void checkLimit(InputStream input, long maxBytes) throws IOException {
    if (input instanceof StageResourceSnapshots.SnapshotStream snapshot) {
      snapshot.initialize(maxBytes);
    } else if (input instanceof PackFile.CapturedStream captured && captured.size() > maxBytes) {
      throw new IOException("Resource exceeds " + maxBytes + " bytes");
    }
  }

  static byte[] json(JsonElement value) {
    return value.toString().getBytes(StandardCharsets.UTF_8);
  }

  static void copy(InputStream input, OutputStream output, long limit) throws IOException {
    byte[] buffer = new byte[8192];
    long remaining = limit;
    while (true) {
      int count = input.read(buffer, 0, (int) Math.min(buffer.length, remaining == Long.MAX_VALUE ? remaining : remaining + 1));
      if (count < 0) {
        return;
      }
      if (count > remaining) {
        throw new IOException("Resource exceeds " + limit + " bytes");
      }
      output.write(buffer, 0, count);
      remaining -= count;
    }
  }
}
