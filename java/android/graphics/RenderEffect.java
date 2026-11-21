// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;

/** Composable post-processing visual effect graph for RenderNodes and Views. */
public final class RenderEffect {
    static final int KIND_OFFSET = 1;
    static final int KIND_BLUR = 2;
    static final int KIND_BITMAP = 3;
    static final int KIND_COLOR_FILTER = 4;
    static final int KIND_BLEND = 5;
    static final int KIND_CHAIN = 6;
    static final int KIND_SHADER = 7;
    static final int KIND_RUNTIME_SHADER = 8;

    final int kind;
    final float f0;
    final float f1;
    final RenderEffect inputA;
    final RenderEffect inputB;
    final Shader.TileMode tileMode;
    final Bitmap bitmap;
    final Rect srcRect;
    final Rect dstRect;
    final ColorFilter colorFilter;
    final BlendMode blendMode;
    final Shader shader;
    final String childShaderName;

    private RenderEffect(
            int kind,
            float f0,
            float f1,
            RenderEffect inputA,
            RenderEffect inputB,
            Shader.TileMode tileMode,
            Bitmap bitmap,
            Rect srcRect,
            Rect dstRect,
            ColorFilter colorFilter,
            BlendMode blendMode,
            Shader shader,
            String childShaderName) {
        this.kind = kind;
        this.f0 = f0;
        this.f1 = f1;
        this.inputA = inputA;
        this.inputB = inputB;
        this.tileMode = tileMode;
        this.bitmap = bitmap;
        this.srcRect = srcRect;
        this.dstRect = dstRect;
        this.colorFilter = colorFilter;
        this.blendMode = blendMode;
        this.shader = shader;
        this.childShaderName = childShaderName;
    }

    public static RenderEffect createOffsetEffect(float offsetX, float offsetY) {
        return createOffsetEffect(offsetX, offsetY, null);
    }

    public static RenderEffect createOffsetEffect(
            float offsetX, float offsetY, RenderEffect input) {
        return new RenderEffect(
                KIND_OFFSET, offsetX, offsetY, input, null, null, null, null, null, null, null, null, null);
    }

    public static RenderEffect createBlurEffect(
            float radiusX, float radiusY, RenderEffect inputEffect, Shader.TileMode edgeTreatment) {
        if (edgeTreatment == null) {
            throw new NullPointerException("edgeTreatment");
        }
        return new RenderEffect(
                KIND_BLUR, radiusX, radiusY, inputEffect, null, edgeTreatment,
                null, null, null, null, null, null, null);
    }

    public static RenderEffect createBlurEffect(
            float radiusX, float radiusY, Shader.TileMode edgeTreatment) {
        return createBlurEffect(radiusX, radiusY, null, edgeTreatment);
    }

    public static RenderEffect createBitmapEffect(Bitmap bitmap) {
        if (bitmap == null) {
            throw new NullPointerException("bitmap");
        }
        Rect full = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        return createBitmapEffect(bitmap, full, full);
    }

    public static RenderEffect createBitmapEffect(Bitmap bitmap, Rect src, Rect dst) {
        if (bitmap == null || dst == null) {
            throw new NullPointerException("bitmap/dst");
        }
        Rect s = src == null ? new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight()) : new Rect(src);
        return new RenderEffect(
                KIND_BITMAP, 0f, 0f, null, null, null, bitmap, s, new Rect(dst),
                null, null, null, null);
    }

    public static RenderEffect createColorFilterEffect(
            ColorFilter colorFilter, RenderEffect renderEffect) {
        if (colorFilter == null) {
            throw new NullPointerException("colorFilter");
        }
        return new RenderEffect(
                KIND_COLOR_FILTER, 0f, 0f, renderEffect, null, null, null, null, null,
                colorFilter, null, null, null);
    }

    public static RenderEffect createColorFilterEffect(ColorFilter colorFilter) {
        return createColorFilterEffect(colorFilter, null);
    }

    public static RenderEffect createBlendModeEffect(
            RenderEffect dst, RenderEffect src, BlendMode blendMode) {
        if (dst == null || src == null || blendMode == null) {
            throw new NullPointerException("dst/src/blendMode");
        }
        return new RenderEffect(
                KIND_BLEND, 0f, 0f, dst, src, null, null, null, null, null, blendMode, null, null);
    }

    public static RenderEffect createChainEffect(RenderEffect outer, RenderEffect inner) {
        if (outer == null || inner == null) {
            throw new NullPointerException("outer/inner");
        }
        return new RenderEffect(
                KIND_CHAIN, 0f, 0f, outer, inner, null, null, null, null, null, null, null, null);
    }

    public static RenderEffect createShaderEffect(Shader shader) {
        if (shader == null) {
            throw new NullPointerException("shader");
        }
        return new RenderEffect(
                KIND_SHADER, 0f, 0f, null, null, null, null, null, null, null, null, shader, null);
    }

    public static RenderEffect createRuntimeShaderEffect(
            RuntimeShader shader, String uniformShaderName) {
        if (shader == null || uniformShaderName == null) {
            throw new NullPointerException("shader/uniformShaderName");
        }
        if (uniformShaderName.isEmpty()) {
            throw new IllegalArgumentException("uniformShaderName must not be empty");
        }
        return new RenderEffect(
                KIND_RUNTIME_SHADER, 0f, 0f, null, null, null, null, null, null, null, null,
                shader, uniformShaderName);
    }

    Bitmap shirazApply(Bitmap sourceContent) {
        int w = sourceContent.getWidth();
        int h = sourceContent.getHeight();
        switch (kind) {
            case KIND_OFFSET: {
                Bitmap in = inputA != null ? inputA.shirazApply(sourceContent) : sourceContent;
                Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(out);
                c.drawBitmap(in, f0, f1, null);
                return out;
            }
            case KIND_COLOR_FILTER: {
                Bitmap in = inputA != null ? inputA.shirazApply(sourceContent) : sourceContent;
                Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(out);
                Paint p = new Paint();
                p.setColorFilter(colorFilter);
                c.drawBitmap(in, 0f, 0f, p);
                return out;
            }
            case KIND_BITMAP: {
                Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(out);
                c.drawBitmap(bitmap, srcRect, dstRect, null);
                return out;
            }
            case KIND_SHADER: {
                Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(out);
                Paint p = new Paint();
                p.setShader(shader);
                c.drawRect(0, 0, w, h, p);
                return out;
            }
            case KIND_CHAIN: {
                Bitmap innerOut = inputB.shirazApply(sourceContent);
                return inputA.shirazApply(innerOut);
            }
            case KIND_BLEND: {
                Bitmap dstBmp = inputA.shirazApply(sourceContent);
                Bitmap srcBmp = inputB.shirazApply(sourceContent);
                Bitmap out = dstBmp.copy(Bitmap.Config.ARGB_8888, true);
                Canvas c = new Canvas(out);
                Paint p = new Paint();
                p.setBlendMode(blendMode);
                c.drawBitmap(srcBmp, 0f, 0f, p);
                return out;
            }
            case KIND_BLUR: {
                Bitmap in = inputA != null ? inputA.shirazApply(sourceContent) : sourceContent;
                int rx = Math.min(32, Math.max(0, Math.round(f0 * 0.5f)));
                int ry = Math.min(32, Math.max(0, Math.round(f1 * 0.5f)));
                if (rx == 0 && ry == 0) {
                    return in.copy(Bitmap.Config.ARGB_8888, true);
                }
                return boxBlur(in, rx, ry);
            }
            default:
                return sourceContent.copy(Bitmap.Config.ARGB_8888, true);
        }
    }

    private static Bitmap boxBlur(Bitmap src, int rx, int ry) {
        int w = src.getWidth();
        int h = src.getHeight();
        int[] in = new int[w * h];
        src.getPixels(in, 0, w, 0, 0, w, h);
        int[] tmp = new int[w * h];
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                int a = 0, r = 0, g = 0, b = 0, cnt = 0;
                for (int dx = -rx; dx <= rx; dx++) {
                    int sx = Math.max(0, Math.min(w - 1, x + dx));
                    int px = in[row + sx];
                    a += (px >>> 24) & 255;
                    r += (px >>> 16) & 255;
                    g += (px >>> 8) & 255;
                    b += px & 255;
                    cnt++;
                }
                tmp[row + x] = ((a / cnt) << 24) | ((r / cnt) << 16) | ((g / cnt) << 8) | (b / cnt);
            }
        }
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                int a = 0, r = 0, g = 0, b = 0, cnt = 0;
                for (int dy = -ry; dy <= ry; dy++) {
                    int sy = Math.max(0, Math.min(h - 1, y + dy));
                    int px = tmp[sy * w + x];
                    a += (px >>> 24) & 255;
                    r += (px >>> 16) & 255;
                    g += (px >>> 8) & 255;
                    b += px & 255;
                    cnt++;
                }
                out[row + x] = ((a / cnt) << 24) | ((r / cnt) << 16) | ((g / cnt) << 8) | (b / cnt);
            }
        }
        Bitmap blurred = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        blurred.setPixels(out, 0, w, 0, 0, w, h);
        return blurred;
    }
}
