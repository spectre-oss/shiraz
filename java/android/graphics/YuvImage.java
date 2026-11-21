// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;

import java.io.IOException;
import java.io.OutputStream;

/** Planar/semi-planar YUV image container with ITU-R BT.601 JPEG compression. */
public class YuvImage {
    private static final int FORMAT_YCBCR_P010 = 54;

    private final int format;
    private final byte[] data;
    private final int[] strides;
    private final int width;
    private final int height;
    private final ColorSpace colorSpace;

    public YuvImage(byte[] yuv, int format, int width, int height, int[] strides) {
        this(yuv, format, width, height, strides, ColorSpace.get(ColorSpace.Named.SRGB));
    }

    public YuvImage(
            byte[] yuv,
            int format,
            int width,
            int height,
            int[] strides,
            ColorSpace colorSpace) {
        if (format != ImageFormat.NV21
                && format != ImageFormat.YUY2
                && format != FORMAT_YCBCR_P010) {
            throw new IllegalArgumentException(
                    "only supports ImageFormat.NV21, ImageFormat.YUY2, and YCBCR_P010");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("width and height must large than 0");
        }
        if (yuv == null) {
            throw new IllegalArgumentException("yuv cannot be null");
        }
        if (colorSpace == null) {
            throw new IllegalArgumentException("ColorSpace cannot be null");
        }
        if (strides == null) {
            this.strides = calculateStrides(width, format);
        } else {
            this.strides = strides.clone();
        }
        this.data = yuv;
        this.format = format;
        this.width = width;
        this.height = height;
        this.colorSpace = colorSpace;
    }

    private static int[] calculateStrides(int width, int format) {
        if (format == ImageFormat.NV21) {
            return new int[]{width, width};
        }
        if (format == ImageFormat.YUY2) {
            return new int[]{width * 2};
        }
        return new int[]{width * 2, width * 2};
    }

    public boolean compressToJpeg(Rect rectangle, int quality, OutputStream stream) {
        if (rectangle == null) {
            throw new IllegalArgumentException("rectangle cannot be null");
        }
        Rect whole = new Rect(0, 0, width, height);
        if (!whole.contains(rectangle)) {
            throw new IllegalArgumentException("rectangle is not inside the image");
        }
        if (quality < 0 || quality > 100) {
            throw new IllegalArgumentException("quality must be 0..100");
        }
        if (stream == null) {
            throw new IllegalArgumentException("stream cannot be null");
        }
        int rw = rectangle.width();
        int rh = rectangle.height();
        if (rw <= 0 || rh <= 0) {
            return false;
        }
        int[] argb = toArgb(rectangle);
        byte[] encoded = shiraz.runtime.NativeImage.jpegEncode(argb, rw, rh, quality);
        if (encoded == null) {
            return false;
        }
        try {
            stream.write(encoded);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public boolean compressToJpegR(YuvImage sdr, int quality, OutputStream stream) {
        if (sdr == null) {
            throw new IllegalArgumentException("SDR input cannot be null");
        }
        if (stream == null) {
            throw new IllegalArgumentException("stream cannot be null");
        }
        if (quality < 0 || quality > 100) {
            throw new IllegalArgumentException("quality must be 0..100");
        }
        if (sdr.getWidth() != width || sdr.getHeight() != height) {
            throw new IllegalArgumentException("SDR and HDR dimensions must match");
        }
        return sdr.compressToJpeg(new Rect(0, 0, sdr.getWidth(), sdr.getHeight()), quality, stream);
    }

    private int[] toArgb(Rect rect) {
        int left = rect.left;
        int top = rect.top;
        int rw = rect.width();
        int rh = rect.height();
        int[] out = new int[rw * rh];
        if (format == ImageFormat.NV21) {
            int yStride = strides[0];
            int vuStride = strides.length > 1 ? strides[1] : width;
            int vuBase = yStride * height;
            for (int r = 0; r < rh; r++) {
                int iy = top + r;
                int yRow = iy * yStride;
                int vuRow = vuBase + (iy >> 1) * vuStride;
                int dstRow = r * rw;
                for (int c = 0; c < rw; c++) {
                    int ix = left + c;
                    int yVal = data[yRow + ix] & 255;
                    int vuIdx = vuRow + (ix & ~1);
                    int vVal = vuIdx < data.length ? (data[vuIdx] & 255) : 128;
                    int uVal = (vuIdx + 1) < data.length ? (data[vuIdx + 1] & 255) : 128;
                    out[dstRow + c] = yuvToArgb(yVal, uVal, vVal);
                }
            }
        } else if (format == ImageFormat.YUY2) {
            int stride = strides[0];
            for (int r = 0; r < rh; r++) {
                int iy = top + r;
                int rowOff = iy * stride;
                int dstRow = r * rw;
                for (int c = 0; c < rw; c++) {
                    int ix = left + c;
                    int pairOff = rowOff + (ix & ~1) * 2;
                    int yVal = data[pairOff + ((ix & 1) << 1)] & 255;
                    int uVal = (pairOff + 1) < data.length ? (data[pairOff + 1] & 255) : 128;
                    int vVal = (pairOff + 3) < data.length ? (data[pairOff + 3] & 255) : 128;
                    out[dstRow + c] = yuvToArgb(yVal, uVal, vVal);
                }
            }
        } else {
            // YCBCR_P010: 16-bit little-endian samples with 10-bit values in high bits
            int yStride = strides[0];
            int uvStride = strides.length > 1 ? strides[1] : width * 2;
            int uvBase = yStride * height;
            for (int r = 0; r < rh; r++) {
                int iy = top + r;
                int yRow = iy * yStride;
                int uvRow = uvBase + (iy >> 1) * uvStride;
                int dstRow = r * rw;
                for (int c = 0; c < rw; c++) {
                    int ix = left + c;
                    int yOff = yRow + ix * 2;
                    int yVal = (yOff + 1) < data.length ? (data[yOff + 1] & 255) : 0;
                    int uvOff = uvRow + (ix & ~1) * 2;
                    int uVal = (uvOff + 1) < data.length ? (data[uvOff + 1] & 255) : 128;
                    int vVal = (uvOff + 3) < data.length ? (data[uvOff + 3] & 255) : 128;
                    out[dstRow + c] = yuvToArgb(yVal, uVal, vVal);
                }
            }
        }
        return out;
    }

    private static int yuvToArgb(int y, int u, int v) {
        int c = y - 16;
        if (c < 0) c = 0;
        int d = u - 128;
        int e = v - 128;
        int r = (298 * c + 409 * e + 128) >> 8;
        int g = (298 * c - 100 * d - 208 * e + 128) >> 8;
        int b = (298 * c + 516 * d + 128) >> 8;
        if (r < 0) r = 0; else if (r > 255) r = 255;
        if (g < 0) g = 0; else if (g > 255) g = 255;
        if (b < 0) b = 0; else if (b > 255) b = 255;
        return 0xff000000 | (r << 16) | (g << 8) | b;
    }

    public byte[] getYuvData() {
        return data;
    }

    public int getYuvFormat() {
        return format;
    }

    public int[] getStrides() {
        return strides;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public ColorSpace getColorSpace() {
        return colorSpace;
    }
}
