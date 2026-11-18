package it.crystalnest.cobweb.api.pack.generation;

import it.crystalnest.cobweb.api.pack.generation.PackGeneration.Task;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * Describes how a declared task can reuse a prior result in its current load scope.
 *
 * @param mode when the result is eligible for reuse.
 * @param callback Java callback cache configuration, or {@code null} for file rules and Java callbacks without caching.
 */
record TaskCachePolicy(Mode mode, @Nullable CallbackRegistration.Policy callback) {
  /**
   * Determines whether this task can use a session or persistent record.
   *
   * @param task generation task.
   * @param root cache scope, or {@code null} when caching is unavailable.
   * @return effective policy for this load.
   */
  static TaskCachePolicy resolve(Task task, @Nullable Path root) {
    var callback = task.registration().policy();
    Mode mode;
    if (root == null || !task.fileRule() && callback == null) {
      mode = Mode.DISABLED;
    } else {
      mode = task.fileRule() || callback.key() != null ? Mode.PERSISTENT : Mode.SESSION;
    }
    return new TaskCachePolicy(mode, callback);
  }

  /**
   * Determines how long an applied callback result can be reused.
   */
  enum Mode {
    /**
     * Runs the task without reuse.
     */
    DISABLED,
    /**
     * Reuses a prior result only in the current process.
     */
    SESSION,
    /**
     * Allows a prior result to be reused after a restart.
     */
    PERSISTENT
  }
}
