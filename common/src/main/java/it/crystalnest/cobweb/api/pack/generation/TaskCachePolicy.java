package it.crystalnest.cobweb.api.pack.generation;

import it.crystalnest.cobweb.api.pack.ResourcePack;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * Resolves caching once for a declared task in its current load scope.
 */
record TaskCachePolicy(Mode mode, @Nullable CallbackRegistration.Policy callback) {
  static TaskCachePolicy resolve(ResourcePack.Task task, @Nullable Path root) {
    var callback = task.registration().policy();
    Mode mode = root == null || !task.fileRule() && callback == null ? Mode.DISABLED
      : task.fileRule() || callback.key() != null ? Mode.PERSISTENT : Mode.SESSION;
    return new TaskCachePolicy(mode, callback);
  }

  enum Mode {
    DISABLED,
    SESSION,
    PERSISTENT
  }
}
