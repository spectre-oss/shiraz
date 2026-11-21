// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;

/** Angular sweep gradient sampled around (cx, cy) in shader-local coordinates. */
public class SweepGradient extends Shader {
    final float cx;
    final float cy;
    final int[] colors;
    final float[] positions;

    public SweepGradient(float cx, float cy, int[] colors, float[] positions) {
        if (colors == null) {
            throw new NullPointerException("colors");
        }
        if (colors.length < 2) {
            throw new IllegalArgumentException("needs >= 2 number of colors");
        }
        if (positions != null && colors.length != positions.length) {
            throw new IllegalArgumentException("color and position arrays must be of equal length");
        }
        if (!Float.isFinite(cx) || !Float.isFinite(cy)
                || Math.abs(cx) > 10000000f || Math.abs(cy) > 10000000f) {
            throw new IllegalArgumentException("SweepGradient center out of range");
        }
        this.cx = cx;
        this.cy = cy;
        this.colors = colors.clone();
        this.positions = buildPositions(this.colors.length, positions);
    }

    public SweepGradient(float cx, float cy, long[] colors, float[] positions) {
        if (colors == null) {
            throw new NullPointerException("colors");
        }
        if (colors.length < 2) {
            throw new IllegalArgumentException("needs >= 2 number of colors");
        }
        if (positions != null && colors.length != positions.length) {
            throw new IllegalArgumentException("color and position arrays must be of equal length");
        }
        if (!Float.isFinite(cx) || !Float.isFinite(cy)
                || Math.abs(cx) > 10000000f || Math.abs(cy) > 10000000f) {
            throw new IllegalArgumentException("SweepGradient center out of range");
        }
        this.cx = cx;
        this.cy = cy;
        this.colors = new int[colors.length];
        for (int i = 0; i < colors.length; i++) {
            this.colors[i] = packedColorToArgb(colors[i]);
        }
        this.positions = buildPositions(this.colors.length, positions);
    }

    public SweepGradient(float cx, float cy, int color0, int color1) {
        this(cx, cy, new int[]{color0, color1}, null);
    }

    public SweepGradient(float cx, float cy, long color0, long color1) {
        this(cx, cy, new long[]{color0, color1}, null);
    }

    private static float[] buildPositions(int count, float[] positions) {
        float[] out = new float[count];
        if (positions == null) {
            for (int i = 0; i < count; i++) {
                out[i] = i / (float) (count - 1);
            }
            return out;
        }
        for (int i = 0; i < count; i++) {
            float v = positions[i];
            if (!Float.isFinite(v)) {
                throw new IllegalArgumentException("SweepGradient position must be finite");
            }
            if (v < 0f) v = 0f;
            else if (v > 1f) v = 1f;
            if (i > 0 && v < out[i - 1]) {
                v = out[i - 1];
            }
            out[i] = v;
        }
        return out;
    }

    static int packedColorToArgb(long color) {
        if ((color & 0x3fL) == 0L) {
            return (int) (color >>> 32);
        }
        float r = halfToFloat((int) ((color >>> 48) & 0xffffL));
        float g = halfToFloat((int) ((color >>> 32) & 0xffffL));
        float b = halfToFloat((int) ((color >>> 16) & 0xffffL));
        float a = ((color >>> 6) & 0x3ffL) / 1023.0f;
        return Color.argb(
                clampByte(Math.round(a * 255f)),
                clampByte(Math.round(r * 255f)),
                clampByte(Math.round(g * 255f)),
                clampByte(Math.round(b * 255f)));
    }

    private static int clampByte(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    private static float halfToFloat(int h) {
        int sign = (h & 0x8000) << 16;
        int exp = (h >>> 10) & 0x1f;
        int mant = h & 0x03ff;
        if (exp == 0) {
            if (mant == 0) {
                return Float.intBitsToFloat(sign);
            }
            while ((mant & 0x0400) == 0) {
                mant <<= 1;
                exp--;
            }
            exp++;
            mant &= ~0x0400;
        } else if (exp == 31) {
            return Float.intBitsToFloat(sign | 0x7f800000 | (mant << 13));
        }
        exp = exp + (127 - 15);
        return Float.intBitsToFloat(sign | (exp << 23) | (mant << 13));
    }

    int sampleAngle(float unitAngle) {
        float t = unitAngle;
        if (t <= positions[0]) {
            return colors[0];
        }
        int last = positions.length - 1;
        if (t >= positions[last]) {
            return colors[last];
        }
        for (int i = 1; i <= last; i++) {
            float p1 = positions[i];
            if (t <= p1) {
                float p0 = positions[i - 1];
                float span = p1 - p0;
                float frac = span <= 1e-7f ? 0f : (t - p0) / span;
                return lerpArgb(colors[i - 1], colors[i], frac);
            }
        }
        return colors[last];
    }

    private static int lerpArgb(int c0, int c1, float t) {
        int a0 = (c0 >>> 24) & 255, r0 = (c0 >>> 16) & 255, g0 = (c0 >>> 8) & 255, b0 = c0 & 255;
        int a1 = (c1 >>> 24) & 255, r1 = (c1 >>> 16) & 255, g1 = (c1 >>> 8) & 255, b1 = c1 & 255;
        int a = clampByte(Math.round(a0 + (a1 - a0) * t));
        int r = clampByte(Math.round(r0 + (r1 - r0) * t));
        int g = clampByte(Math.round(g0 + (g1 - g0) * t));
        int b = clampByte(Math.round(b0 + (b1 - b0) * t));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    Bitmap raster(int width, int height, float[] canvasMatrix) {
        float[] inv = inverse(canvasMatrix);
        Bitmap out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        int[] dst = out.pixels;
        final double invTwoPi = 1.0 / (Math.PI * 2.0);
        for (int y = 0; y < height; y++) {
            float py = y + 0.5f;
            int row = y * width;
            for (int x = 0; x < width; x++) {
                float px = x + 0.5f;
                float lx = inv[0] * px + inv[2] * py + inv[4] - cx;
                float ly = inv[1] * px + inv[3] * py + inv[5] - cy;
                double angle = Math.atan2(ly, lx) * invTwoPi;
                if (angle < 0.0) {
                    angle += 1.0;
                }
                dst[row + x] = sampleAngle((float) angle);
            }
        }
        return out;
    }
}
