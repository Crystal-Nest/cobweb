package it.crystalnest.cobweb.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.attribute.PermittedSubclassesAttribute;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Gradle task that creates a vanilla Minecraft JAR with an unsealed {@code Holder} for common compilation.
 * Removes the {@link PermittedSubclassesAttribute} using the JDK class-file API, allowing common code to implement {@code Holder} without compiling against loader specific Minecraft patches.
 * Updates the attached source, when present, to match the transformed class in the IDE.
 * The generated JAR is a development dependency and is not included in published mod artifacts.
 */
@CacheableTask
public abstract class UnsealHolder extends DefaultTask {
  /**
   * JAR entry path for the {@code Holder} class.
   */
  private static final String HOLDER_CLASS = "net/minecraft/core/Holder.class";

  /**
   * JAR entry path for the attached {@code Holder} source.
   */
  private static final String HOLDER_SOURCE = "net/minecraft/core/Holder.java";

  /**
   * Returns the vanilla Minecraft JAR property, optionally containing attached sources.
   *
   * @return input JAR {@link RegularFileProperty}.
   */
  @InputFile
  @PathSensitive(PathSensitivity.NONE)
  public abstract RegularFileProperty getInputJar();

  /**
   * Returns the generated Minecraft JAR property with an unsealed {@code Holder}.
   *
   * @return output JAR {@link RegularFileProperty}.
   */
  @OutputFile
  public abstract RegularFileProperty getOutputJar();

  /**
   * Copies the input JAR, removing the sealing attribute from {@link #HOLDER_CLASS}.
   * Transforms {@link #HOLDER_SOURCE} when present and preserves the contents of all other entries.
   * Uses a fixed timestamp for each output entry to make the generated JAR reproducible.
   *
   * @throws IOException if the input JAR can't be read or the output JAR can't be written.
   * @throws GradleException if the input JAR doesn't contain {@link #HOLDER_CLASS} or the source declaration is unsupported.
   */
  @TaskAction
  public void transform() throws IOException {
    var destination = getOutputJar().get().getAsFile().toPath();
    try (var input = new ZipFile(getInputJar().get().getAsFile())) {
      if (input.getEntry(HOLDER_CLASS) == null) {
        throw new GradleException("Minecraft input jar does not contain " + HOLDER_CLASS);
      }
      Files.createDirectories(destination.getParent());
      try (var output = new ZipOutputStream(Files.newOutputStream(destination))) {
        var entries = input.entries();
        while (entries.hasMoreElements()) {
          var entry = entries.nextElement();
          var copiedEntry = new ZipEntry(entry.getName());
          copiedEntry.setTime(0L);
          output.putNextEntry(copiedEntry);
          try (var stream = input.getInputStream(entry)) {
            if (HOLDER_CLASS.equals(entry.getName())) {
              var classFile = ClassFile.of();
              output.write(classFile.transformClass(classFile.parse(stream.readAllBytes()), ClassTransform.dropping(element -> element instanceof PermittedSubclassesAttribute)));
            } else if (HOLDER_SOURCE.equals(entry.getName())) {
              output.write(unsealSource(stream.readAllBytes()));
            } else {
              stream.transferTo(output);
            }
          }
          output.closeEntry();
        }
      }
    }
  }

  /**
   * Removes the {@code sealed} modifier and {@code permits} clause from the attached {@code Holder} source.
   * Removes the {@code non-sealed} modifier from {@code Holder.Reference} to match its unsealed parent.
   *
   * @param bytes original source encoded as UTF-8.
   * @return transformed source encoded as UTF-8.
   * @throws GradleException if the sealed {@code Holder} declaration doesn't match the expected source.
   */
  private static byte[] unsealSource(byte[] bytes) {
    var source = new String(bytes, StandardCharsets.UTF_8);
    var declaration = "public sealed interface Holder<T> permits Holder.Direct, Holder.Reference {";
    if (!source.contains("sealed interface Holder") || source.contains(declaration)) {
      // IntelliJ uses the combined sources/classes jar. Mirror the bytecode change in its source. Reference has no non-sealed bytecode flag, but its source must match the unsealed parent.
      return source.replace(declaration, "public interface Holder<T> {").replace("non-sealed class Reference<T>", "class Reference<T>").getBytes(StandardCharsets.UTF_8);
    }
    throw new GradleException("Holder's source declaration changed; update UnsealHolder's source transformation");
  }
}
