// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;

/** Filter type; PorterDuff, BlendMode, Lighting, and RGBA matrix filters are implemented. */
public class ColorFilter {
    public static void shirazValidate(ColorFilter value) {
        if (value == null) return;
        if (value instanceof PorterDuffColorFilter
                || value instanceof ColorMatrixColorFilter
                || value instanceof LightingColorFilter) {
            return;
        }
        if (value instanceof BlendModeColorFilter) {
            ((BlendModeColorFilter) value).mMode.legacy();
            return;
        }
        throw new UnsupportedOperationException("This color filter is not implemented");
    }

    public static int shirazMode(ColorFilter value) {
        shirazValidate(value);
        if (value == null) return -1;
        if (value instanceof ColorMatrixColorFilter || value instanceof LightingColorFilter) {
            return -2;
        }
        if (value instanceof BlendModeColorFilter) {
            return ((BlendModeColorFilter) value).mMode.legacy().ordinal();
        }
        return ((PorterDuffColorFilter) value).mode.ordinal();
    }

    public static int shirazColor(ColorFilter value) {
        shirazValidate(value);
        if (value instanceof PorterDuffColorFilter) {
            return ((PorterDuffColorFilter) value).color;
        }
        if (value instanceof BlendModeColorFilter) {
            return ((BlendModeColorFilter) value).mColor;
        }
        return 0;
    }

    public static float[] shirazMatrix(ColorFilter value) {
        shirazValidate(value);
        if (value instanceof ColorMatrixColorFilter) {
            return ((ColorMatrixColorFilter) value).shirazMatrix();
        }
        if (value instanceof LightingColorFilter) {
            return ((LightingColorFilter) value).shirazMatrix();
        }
        return null;
    }

    public static int shirazApply(ColorFilter value, int color) {
        if (value instanceof ColorMatrixColorFilter || value instanceof LightingColorFilter) {
            return shiraz.runtime.NativeBitmap.filterColorMatrix(color, shirazMatrix(value));
        }
        return value == null
                ? color
                : shiraz.runtime.NativeBitmap.filterColor(shirazColor(value), color, shirazMode(value));
    }
}
