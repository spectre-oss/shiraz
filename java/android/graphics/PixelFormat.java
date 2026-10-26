// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;
public class PixelFormat {
    public static final int RGBA_8888=1, RGBX_8888=2, RGB_888=3, RGB_565=4;
    public static final int RGBA_5551=6,RGBA_4444=7,A_8=8,L_8=9,LA_88=10,RGB_332=11;
    public static final int YCbCr_422_SP=16,YCbCr_420_SP=17,YCbCr_422_I=20,RGBA_F16=22,RGBA_1010102=43,JPEG=256;
    public static final int UNKNOWN=0,TRANSLUCENT=-3,TRANSPARENT=-2,OPAQUE=-1;
    public int bytesPerPixel,bitsPerPixel;
    private static final int[] FORMATS={RGBA_8888,RGBX_8888,RGB_888,RGB_565,RGBA_5551,RGBA_4444,A_8,L_8,LA_88,RGB_332,YCbCr_422_SP,YCbCr_420_SP,YCbCr_422_I,RGBA_F16,RGBA_1010102};
    private static final int[] BITS={32,32,24,16,16,16,8,8,16,8,16,12,16,64,32};
    private static final int[] BYTES={4,4,3,2,2,2,1,1,2,1,1,1,1,8,4};
    public static void getPixelFormatInfo(int format,PixelFormat info){
        for(int i=0;i<FORMATS.length;i++)if(FORMATS[i]==format){info.bitsPerPixel=BITS[i];info.bytesPerPixel=BYTES[i];return;}
        throw new IllegalArgumentException("Unknown pixel format "+format);
    }
    public static boolean formatHasAlpha(int format){return format==RGBA_8888||format==RGBA_5551||format==RGBA_4444||format==A_8||format==LA_88||format==RGBA_F16||format==RGBA_1010102||format==TRANSLUCENT||format==TRANSPARENT;}
}
