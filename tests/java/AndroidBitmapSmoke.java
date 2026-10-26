// SPDX-License-Identifier: GPL-3.0-only
import android.graphics.Bitmap;
public final class AndroidBitmapSmoke {
    private static native void capacity(Bitmap[] bitmaps);
    private static native void exercise(Bitmap bitmap, int mode);
    public static void main(String[] args) {
        System.loadLibrary("shiraz-test-android-bitmap");
        for (int mode = 0; mode < 4; mode++) {
            Bitmap b = Bitmap.createBitmap(2, 1, mode == 1 ? Bitmap.Config.ALPHA_8 : Bitmap.Config.ARGB_8888);
            b.setPixel(0, 0, 0x80ff4000); b.setPixel(1, 0, 0xff112233);
            exercise(b, mode);
            if (mode == 3) {
                if (!b.isRecycled()) throw new AssertionError("recycled bitmap revived");
            } else if (mode == 1) {
                if (b.getPixel(0, 0) != 0x11000000 || b.getPixel(1, 0) != 0) throw new AssertionError("alpha writeback");
            } else {
                if (b.getPixel(0, 0) != 0x80ff4000) throw new AssertionError("unmodified alpha color lost precision");
                if (b.getPixel(1, 0) != (mode == 2 ? 0xff112233 : 0x8000ff00)) throw new AssertionError("RGBA writeback");
            }
        }
        Bitmap[] bitmaps = new Bitmap[65];
        for (int i = 0; i < bitmaps.length; i++) bitmaps[i] = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        capacity(bitmaps);
        System.out.println("ANDROID_BITMAP_FORMAT_LIFETIME_WRITEBACK_OK");
    }
}
