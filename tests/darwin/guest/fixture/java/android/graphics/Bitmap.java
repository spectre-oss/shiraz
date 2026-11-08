// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;
/** CPU pixel-storage ABI fixture. Production Bitmap lives under java/android. */
public final class Bitmap {
    public final int width = 2, height = 1;
    public final int[] pixels = {0x80ff4000, 0xff112233};
    public int preparations;
    public int getRowBytes() { return width * 4; }
    public void shirazPrepareNativePixels() {
        if (pixels.length != width * height) throw new IllegalStateException("pixel storage");
        preparations++;
    }
}
