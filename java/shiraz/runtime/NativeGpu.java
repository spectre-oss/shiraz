// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
/**
 * Skia surfaces live on the presentation thread. Pixel access is an explicit
 * synchronization.
 */
public final class NativeGpu {
  static { System.loadLibrary("shiraz-gpu"); }
  private NativeGpu() {}
  // Geometry only: valid without a GL context, including software fallback.
  public static native float[] pathOp(float[] first, int firstFill, float[] second,
                                     int secondFill, int operation);
  public static native boolean available();
  public static native boolean valid(long surface);
  public static native long create(int width, int height, int[] pixels);
  public static native void release(long surface);
  public static native void read(long surface, int[] pixels);
  public static native void clear(long surface, int color);
  public static native void copyInto(long source, long destination);
  public static native void post(long surface);
  public static native void begin(long surface);
  public static native void save(long surface);
  public static native void restore(long surface);
  public static native void matrix(long surface, float[] matrix);
  public static native void paint(long surface, float[] transform, int[] values, float[] scalars,
                                  float[] colorMatrix, long shaderImage,
                                  float[] shaderMatrix, int[] colors,
                                  float[] positions, float[] points,
                                  float[] dash);
  public static native void layer(long surface, float[] bounds);
  public static native void shape(long surface, int kind, float left, float top,
                                  float right, float bottom, float rx,
                                  float ry);
  public static native boolean clipRect(long surface, float left, float top,
                                        float right, float bottom,
                                        boolean difference);
  public static native void path(long surface, float[] commands, int fill);
  public static native void clip(long surface, float[] commands, int fill,
                                 boolean difference);
  public static native int[] clipBounds(long surface);
  public static native void image(long surface, long image, float[] source,
                                  float[] destination, boolean filter);
  public static native void shadow(long surface, float[] commands, float z,
                                   float opacity);
  public static native void ripple(long surface, float[] geometry, int color,
                                   int sparkle);
  public static native void color(long surface, int color);
}
