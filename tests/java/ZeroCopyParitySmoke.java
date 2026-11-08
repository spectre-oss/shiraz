// SPDX-License-Identifier: GPL-3.0-only
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ShirazWindowDraw;

/**
 * M4b H8 (build/macos/m4b/design/DESIGN-v2.md section 9): on a host JVM run with
 * -Dos.name=Linux and without any Shiraz native library, the zero-copy consumer
 * gate stays closed and touches no native. ShirazWindowDraw.available() would
 * initialise shiraz.runtime.NativeGpu (System.loadLibrary("shiraz-gpu")) if the
 * gate were open, which throws UnsatisfiedLinkError here, so a pass also proves
 * that no darwin native was reached. Surface.shirazDraw's macOS branch begins with
 * ShirazWindowDraw.MAC &&, so with MAC false it falls through to the unchanged
 * publishWindow + drawBitmap(front, null, Rect(0,0,w,h), null) statement.
 * Limits: android.view.Surface itself is not instantiated (its static initialiser
 * loads libandroid), so that fall-through is shown by the MAC value plus review.
 */
public final class ZeroCopyParitySmoke {
    static int failures;
    static void check(boolean ok, String what) {
        System.out.println((ok ? "ok   " : "FAIL ") + what);
        if (!ok) failures++;
    }
    public static void main(String[] args) {
        String os = System.getProperty("os.name");
        check("Linux".equals(os), "os.name reads back as Linux (else the test is void): " + os);
        check(!ShirazWindowDraw.MAC, "ShirazWindowDraw.MAC is false");
        boolean available;
        try {
            available = ShirazWindowDraw.available();
        } catch (Throwable error) {
            check(false, "available() touched a native: " + error);
            available = true;
        }
        check(!available, "ShirazWindowDraw.available() is false");
        Canvas canvas = new Canvas();
        try {
            check(!ShirazWindowDraw.draw(canvas, 1L, 4, 4, false), "ShirazWindowDraw.draw() declines a CPU Canvas");
        } catch (Throwable error) {
            check(false, "draw() touched a native: " + error);
        }
        System.out.println("ZERO_COPY_PARITY " + (failures == 0 ? "OK" : "FAILED failures=" + failures));
        System.exit(failures == 0 ? 0 : 1);
    }
}
