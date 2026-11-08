// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.apiprobe;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import java.nio.ByteBuffer;

final class NativeProbe {
  static native int arithmetic(int a, int b);
  static native String echo(String value);
  static native int direct(ByteBuffer buffer);
  static native int looper();
  static native int extended(String name, int[] array);
  static int javaCallback() {
    return 42;
  }
  static native int window(Surface surface);
  static native int bitmap(Bitmap bitmap);
  static void run(Context context, String id) throws Exception {
    // Missing optional artifact is SKIPPED, not a platform compatibility verdict.
    if (!new java.io.File(context.getApplicationInfo().nativeLibraryDir, "libshiraz_api_probe.so")
            .isFile())
      throw new ProbeChecks.Skip(
          "Build with --ndk to include libshiraz_api_probe.so for the device ABI");
    System.loadLibrary("shiraz_api_probe");
    switch (id) {
      case "jni":
        ProbeChecks.check(arithmetic(19, 23) == 42);
        ProbeChecks.eq(echo("سلام 🌍"), "سلام 🌍");
        ByteBuffer b = ByteBuffer.allocateDirect(4);
        b.put(0, (byte) 42);
        ProbeChecks.check(direct(b) == 42);
        break;
      case "looper":
        ProbeChecks.check(looper() == 42);
        break;
      case "window":
        SurfaceTexture texture = new SurfaceTexture(false);
        Surface surface = null;
        try {
          texture.setDefaultBufferSize(16, 16);
          surface = new Surface(texture);
          ProbeChecks.check(window(surface) > 0);
        } finally {
          if (surface != null)
            surface.release();
          texture.release();
        }
        break;
      case "bitmap":
        Bitmap bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
        try {
          ProbeChecks.check(bitmap(bitmap) == 0 && bitmap.getPixel(0, 0) == 0xffff0000);
        } finally {
          bitmap.recycle();
        }
        break;
      case "jni_array":
        int[] array = {1, 2, 3};
        ProbeChecks.check(extended(id, array) == 42 && array[1] == 42);
        break;
      case "jni_exception":
        boolean caught = false;
        try {
          extended(id, null);
        } catch (IllegalStateException expected) {
          caught = "native probe".equals(expected.getMessage());
        }
        ProbeChecks.check(caught);
        break;
      case "jni_callback":
      case "pthread":
      case "shared_memory":
      case "hardware_buffer":
      case "dlopen":
      case "clock":
        ProbeChecks.check(extended(id, null) == 42);
        break;
      default:
        throw new IllegalArgumentException(id);
    }
  }
}
