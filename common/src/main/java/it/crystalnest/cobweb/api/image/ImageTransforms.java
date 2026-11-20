package it.crystalnest.cobweb.api.image;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.util.ARGB;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Applies pixel transforms to RGBA {@link NativeImage}s independently of resource loading.
 */
public final class ImageTransforms {
  /**
   * Red contribution to the brightness score, using the Rec. 709 coefficient scaled by 10,000.
   */
  private static final int RED_WEIGHT = 2126;

  /**
   * Green contribution to the brightness score, using the Rec. 709 coefficient scaled by 10,000.
   */
  private static final int GREEN_WEIGHT = 7152;

  /**
   * Blue contribution to the brightness score, using the Rec. 709 coefficient scaled by 10,000.
   */
  private static final int BLUE_WEIGHT = 722;

  private ImageTransforms() {}

  /**
   * Recolors visible template pixels by matching dark-to-light ranks in the reference palette.
   * Fully transparent template pixels and pixels outside the mask retain their original color.
   * If the template has no eligible pixels, returns a copy without reading the reference.
   *
   * @param template RGBA image to recolor.
   * @param reference RGBA image supplying visible RGB shades. Its dimensions may differ from the template's.
   * @param mask RGBA image matching the template's dimensions, or {@code null} to recolor all visible template pixels.
   * @return a new image with the template's dimensions and alpha, which the caller must close.
   * @throws IllegalArgumentException if the mask dimensions differ, an image read by the operation is not RGBA, or eligible template pixels have no visible reference colors.
   */
  public static NativeImage recolor(NativeImage template, NativeImage reference, @Nullable NativeImage mask) {
    if (mask != null) {
      requireSameSize(template, mask);
    }
    var source = palette(template, mask);
    if (source.length > 0) {
      var target = palette(reference, null);
      if (target.length == 0) {
        throw new IllegalArgumentException("Texture palette has no visible colors");
      }
      Map<Integer, Integer> remap = new HashMap<>();
      for (int index = 0; index < source.length; index++) {
        // Map both palette endpoints exactly and round intermediate ranks to the nearest target shade.
        // A single source shade has no range, so choose the lower of the target's middle shades.
        int rank = source.length == 1 ? (target.length - 1) / 2 : Math.round(index * (target.length - 1F) / (source.length - 1));
        remap.put(source[index], target[rank]);
      }
      return transform(template, (x, y, pixel) -> {
        if (eligible(pixel, mask, x, y)) {
          return ARGB.color(ARGB.alpha(pixel), remap.get(ARGB.transparent(pixel)));
        }
        return pixel;
      });
    }
    return transform(template, (x, y, pixel) -> pixel);
  }

  /**
   * Composites a foreground over a same-size background using Minecraft's source-over blending in {@link ARGB#alphaBlend(int, int)}.
   *
   * @param background RGBA image behind the foreground.
   * @param foreground RGBA image composited over the background.
   * @return a new composited image, which the caller must close.
   * @throws IllegalArgumentException if the image dimensions differ or either image is not RGBA.
   */
  public static NativeImage overlay(NativeImage background, NativeImage foreground) {
    requireSameSize(background, foreground);
    return transform(background, (x, y, pixel) -> ARGB.alphaBlend(pixel, foreground.getPixel(x, y)));
  }

  /**
   * Collects distinct RGB shades from eligible pixels and sorts them by brightness, breaking ties by packed RGB value.
   *
   * @param image image supplying colors.
   * @param mask matching region mask, or {@code null} to use every visible pixel.
   * @return sorted array of RGB shades with the alpha byte cleared.
   */
  private static int[] palette(NativeImage image, @Nullable NativeImage mask) {
    Set<Integer> shades = new HashSet<>();
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        int pixel = image.getPixel(x, y);
        if (eligible(pixel, mask, x, y)) {
          // Clear the high alpha byte so pixels with the same RGB share a palette entry at every opacity.
          shades.add(ARGB.transparent(pixel));
        }
      }
    }
    return shades.stream().sorted(Comparator.comparingInt(ImageTransforms::luminance).thenComparingInt(Integer::intValue)).mapToInt(Integer::intValue).toArray();
  }

  /**
   * Checks whether a pixel contributes to the palette or can be recolored.
   *
   * @param pixel packed ARGB pixel from the image.
   * @param mask matching region mask, or {@code null} if every visible pixel is eligible.
   * @param x pixel column in the image and mask.
   * @param y pixel row in the image and mask.
   * @return whether the pixel and, when supplied, the mask pixel have nonzero alpha.
   */
  private static boolean eligible(int pixel, @Nullable NativeImage mask, int x, int y) {
    return ARGB.alpha(pixel) != 0 && (mask == null || ARGB.alpha(mask.getPixel(x, y)) != 0);
  }

  /**
   * Scores brightness with the Rec. 709 weights 0.2126, 0.7152 and 0.0722, scaled by 10,000 for integer sorting.
   * Applies the weights directly to encoded sRGB channels to rank palette shades without linearizing them.
   *
   * @param color packed RGB shade.
   * @return weighted brightness score.
   */
  private static int luminance(int color) {
    return RED_WEIGHT * ARGB.red(color) + GREEN_WEIGHT * ARGB.green(color) + BLUE_WEIGHT * ARGB.blue(color);
  }

  /**
   * Checks that both images can be traversed with the same pixel coordinates.
   *
   * @param first image defining the expected dimensions.
   * @param second image to check.
   * @throws IllegalArgumentException if the image dimensions differ.
   */
  private static void requireSameSize(NativeImage first, NativeImage second) {
    if (first.getWidth() != second.getWidth() || first.getHeight() != second.getHeight()) {
      throw new IllegalArgumentException("Texture dimensions must match");
    }
  }

  /**
   * Applies a pixel operation across the image and closes the output if processing fails.
   *
   * @param image source image.
   * @param operation operation applied to each source pixel.
   * @return a new image with the source dimensions, which the caller must close.
   */
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

  /**
   * Transforms a pixel with access to its position in the source image.
   */
  @FunctionalInterface
  private interface PixelTransform {
    /**
     * Returns the output color for a source pixel.
     *
     * @param x source pixel column.
     * @param y source pixel row.
     * @param pixel packed ARGB color of the source pixel.
     * @return packed ARGB color of the output pixel.
     */
    int apply(int x, int y, int pixel);
  }
}
