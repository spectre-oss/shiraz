// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;
import shiraz.runtime.NativeGpu;
/**
 * macOS M4b consumer entry (build/macos/m4b/design/DESIGN-v2.md section 5.1): draws
 * the newest IOSurface frame of a Shiraz native window straight into a GPU Canvas,
 * with no CPU copy. Off macOS nothing here touches a native, and callers keep their
 * existing CPU path.
 */
public final class ShirazWindowDraw {
  // Explicit platform check. ART reports os.name=Linux on every host, so this is
  // shiraz.runtime.HostOs (uname(2) under ART, os.name on a host JVM).
  public static final boolean MAC = shiraz.runtime.HostOs.darwin();
  private static final boolean REQUIRE =
      "require".equals(System.getenv("SHIRAZ_EGL_ZERO_COPY"));
  private static boolean linkMissing;
  private static final float[] destination = new float[4];
  private ShirazWindowDraw() {}
  /** False off macOS, under SHIRAZ_EGL_ZERO_COPY=0, and when the GPU consumer is unavailable. */
  public static synchronized boolean available() {
    if (!MAC || linkMissing) return false;
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
    if (!MAC || !(canvas instanceof GpuCanvas)) return false;
    synchronized (ShirazWindowDraw.class) {
      destination[0] = 0; destination[1] = 0; destination[2] = width; destination[3] = height;
      return ((GpuCanvas) canvas).shirazDrawWindow(window, destination, opaque);
    }
  }
}
