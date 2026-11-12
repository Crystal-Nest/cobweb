package it.crystalnest.cobweb.api.pack;

import com.google.gson.JsonElement;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Encodes shared file contents and bounds reads before they enter temporary storage.
 */
final class PackIO {
  private PackIO() {}

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
