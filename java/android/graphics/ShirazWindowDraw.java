// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;
import shiraz.runtime.NativeGpu;
/**
 * Zero-copy consumer entry (macOS IOSurface + Linux GBM/DMA-BUF): draws the newest
 * queued GPU frame of a Shiraz native window straight into a GPU Canvas with no CPU copy.
 * On unsupported hosts or under SHIRAZ_EGL_ZERO_COPY=0, callers keep the CPU path.
 */
public final class ShirazWindowDraw {
  // Explicit platform check. ART reports os.name=Linux on every host, so this is
  // shiraz.runtime.HostOs (uname(2) under ART, os.name on a host JVM).
  public static final boolean MAC = shiraz.runtime.HostOs.darwin();
  public static final boolean ZERO_COPY_HOST = MAC || shiraz.runtime.HostOs.linux();
  private static final boolean REQUIRE =
      "require".equals(System.getenv("SHIRAZ_EGL_ZERO_COPY"));
  private static boolean linkMissing;
  private static final float[] destination = new float[4];
  private ShirazWindowDraw() {}
  /** False on unsupported hosts, under SHIRAZ_EGL_ZERO_COPY=0, or when unavailable. */
  public static synchronized boolean available() {
    if (!ZERO_COPY_HOST || linkMissing) return false;
    try {
      return NativeGpu.windowSupported();
    } catch (UnsatisfiedLinkError error) {
      linkMissing = true;
      System.err.println("SHIRAZ_EGL_ZERO_COPY consumer-fallback reason=natives-missing");
      if (REQUIRE) {
        System.err.println("SHIRAZ_RUNTIME_FATAL zero-copy reason=consumer-natives-missing");
        System.err.flush();
        Runtime.getRuntime().halt(134);
      }
      return false;
    }
  }
  /** Draws WINDOW's newest queued frame over (0,0,w,h); false means use the CPU path. */
  public static boolean draw(Canvas canvas, long window, int width, int height, boolean opaque) {
    if (!ZERO_COPY_HOST || !(canvas instanceof GpuCanvas)) return false;
    synchronized (ShirazWindowDraw.class) {
      destination[0] = 0; destination[1] = 0; destination[2] = width; destination[3] = height;
      return ((GpuCanvas) canvas).shirazDrawWindow(window, destination, opaque);
    }
  }
}
