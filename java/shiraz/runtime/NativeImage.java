// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
public final class NativeImage {
    private NativeImage() { }
    static { System.loadLibrary("shiraz-image"); }
    public static native int[] webp(byte[] data,int offset,int length,boolean boundsOnly,int[] dimensions);
    public static native int[] jpeg(byte[] data,int offset,int length,boolean boundsOnly,int[] dimensions);
    public static native int[] png(byte[] data,int offset,int length,boolean boundsOnly,int[] dimensions);
    public static native byte[] webpEncode(int[] pixels,int width,int height,int quality,boolean lossless);
    public static native byte[] jpegEncode(int[] pixels,int width,int height,int quality);
}
