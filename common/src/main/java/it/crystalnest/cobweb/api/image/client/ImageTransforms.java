package it.crystalnest.cobweb.api.image.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.util.ARGB;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Applies pixel transforms to client images independently of resource loading.
 */
public final class ImageTransforms {
  private ImageTransforms() {}

  /**
   * Recolors visible template pixels by matching dark-to-light ranks in the reference image.
   * Returns a new image with the template's dimensions and alpha.
   *
   * @param template original image.
   * @param reference image supplying visible RGB shades.
   * @param mask optional same-size image whose nontransparent pixels mark the area to recolor.
   * @return caller-owned image.
   * @throws IllegalArgumentException if the mask dimensions differ or the reference supplies no visible colors for an eligible template.
   */
  public static NativeImage recolor(NativeImage template, NativeImage reference, @Nullable NativeImage mask) {
    if (mask != null) {
      requireSameSize(template, mask);
    }
    var source = palette(template, mask);
    if (source.length == 0) {
      return transform(template, (x, y, pixel) -> pixel);
    }
    var target = palette(reference, null);
    if (target.length == 0) {
      throw new IllegalArgumentException("Texture palette has no visible colors");
    }
    Map<Integer, Integer> remap = new HashMap<>();
    for (int index = 0; index < source.length; index++) {
      int rank = source.length == 1 ? (target.length - 1) / 2 : Math.round(index * (target.length - 1f) / (source.length - 1));
      remap.put(source[index], target[rank]);
    }
    return transform(template, (x, y, pixel) -> {
      if (!eligible(pixel, mask, x, y)) {
        return pixel;
      }
      return ARGB.color(ARGB.alpha(pixel), remap.get(pixel & 0xFFFFFF));
    });
  }

  /**
   * Composites a foreground over a same-size background using source-over alpha.
   *
   * @param background background image.
   * @param foreground foreground image.
   * @return caller-owned image.
   * @throws IllegalArgumentException if the image dimensions differ.
   */
  public static NativeImage overlay(NativeImage background, NativeImage foreground) {
    requireSameSize(background, foreground);
    return transform(background, (x, y, pixel) -> composite(pixel, foreground.getPixel(x, y)));
  }

  private static int composite(int background, int foreground) {
    int above = ARGB.alpha(foreground) * 255;
    int below = ARGB.alpha(background) * (255 - ARGB.alpha(foreground));
    int total = above + below;
    if (total == 0) {
      return background;
    }
    return ARGB.color((total + 127) / 255,
      (ARGB.red(foreground) * above + ARGB.red(background) * below + total / 2) / total,
      (ARGB.green(foreground) * above + ARGB.green(background) * below + total / 2) / total,
      (ARGB.blue(foreground) * above + ARGB.blue(background) * below + total / 2) / total);
  }

  private static int[] palette(NativeImage image, @Nullable NativeImage mask) {
    Set<Integer> shades = new HashSet<>();
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        int pixel = image.getPixel(x, y);
        if (eligible(pixel, mask, x, y)) {
          shades.add(pixel & 0xFFFFFF);
        }
      }
    }

    return shades.stream().sorted(Comparator.comparingInt(ImageTransforms::luminance).thenComparingInt(Integer::intValue)).mapToInt(Integer::intValue).toArray();
  }

  private static boolean eligible(int pixel, @Nullable NativeImage mask, int x, int y) {
    return ARGB.alpha(pixel) != 0 && (mask == null || ARGB.alpha(mask.getPixel(x, y)) != 0);
  }

  private static int luminance(int color) {
    return 2126 * ARGB.red(color) + 7152 * ARGB.green(color) + 722 * ARGB.blue(color);
  }

  private static void requireSameSize(NativeImage first, NativeImage second) {
    if (first.getWidth() != second.getWidth() || first.getHeight() != second.getHeight()) {
      throw new IllegalArgumentException("Texture dimensions must match");
    }
  }

  private static NativeImage transform(NativeImage image, PixelTransform operation) {
    var result = new NativeImage(image.getWidth(), image.getHeight(), false);
    try {
      for (int y = 0; y < image.getHeight(); y++) {
        for (int x = 0; x < image.getWidth(); x++) {
          result.setPixel(x, y, operation.apply(x, y, image.getPixel(x, y)));
        }
      }
      return result;
    } catch (RuntimeException | Error failure) {
      result.close();
      throw failure;
    }
  }

  @FunctionalInterface
  private interface PixelTransform {
    int apply(int x, int y, int pixel);
  }
}
