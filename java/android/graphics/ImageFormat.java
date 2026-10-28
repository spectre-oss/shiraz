// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;

/** Image buffer format identifiers and their uncompressed storage density. */
public class ImageFormat {
    public static final int UNKNOWN=0, RGB_565=4, NV16=16, NV21=17, YUY2=20;
    public static final int RAW_SENSOR=32, PRIVATE=34, YUV_420_888=35, RAW_PRIVATE=36;
    public static final int RAW10=37, RAW12=38, YUV_422_888=39, YUV_444_888=40;
    public static final int FLEX_RGB_888=41, FLEX_RGBA_8888=42, JPEG=256, DEPTH_POINT_CLOUD=257;
    public static final int YV12=0x32315659, Y8=0x20203859, Y16=0x20363159, DEPTH16=0x44363159;

    public static int getBitsPerPixel(int format) {
        switch (format) {
            case Y8: return 8;
            case RAW10: return 10;
            case NV21: case YV12: case YUV_420_888: case RAW12: return 12;
            case RGB_565: case NV16: case YUY2: case RAW_SENSOR:
            case YUV_422_888: case Y16: case DEPTH16: return 16;
            case YUV_444_888: case FLEX_RGB_888: return 24;
            case FLEX_RGBA_8888: return 32;
            default: return -1;
        }
    }
    public static boolean isPublicFormat(int format) {
        switch (format) {
            case RGB_565: case NV16: case NV21: case YUY2: case RAW_SENSOR:
            case PRIVATE: case YUV_420_888: case RAW_PRIVATE: case RAW10: case RAW12:
            case YUV_422_888: case YUV_444_888: case FLEX_RGB_888: case FLEX_RGBA_8888:
            case JPEG: case DEPTH_POINT_CLOUD: case YV12: case DEPTH16: return true;
            default: return false;
        }
    }
}
