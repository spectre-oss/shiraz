// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;
/** Filter type; PorterDuff and RGBA matrix filters are implemented. */
public class ColorFilter {
    public static void shirazValidate(ColorFilter value) {
        if (value != null && !(value instanceof PorterDuffColorFilter) && !(value instanceof ColorMatrixColorFilter)) throw new UnsupportedOperationException("This color filter is not implemented");
    }
    public static int shirazMode(ColorFilter value) { shirazValidate(value); return value == null ? -1 : value instanceof ColorMatrixColorFilter ? -2 : ((PorterDuffColorFilter)value).mode.ordinal(); }
    public static int shirazColor(ColorFilter value) { shirazValidate(value); return value instanceof PorterDuffColorFilter ? ((PorterDuffColorFilter)value).color : 0; }
    public static float[] shirazMatrix(ColorFilter value) { shirazValidate(value); return value instanceof ColorMatrixColorFilter ? ((ColorMatrixColorFilter)value).shirazMatrix() : null; }
    public static int shirazApply(ColorFilter value,int color) {
        if(value instanceof ColorMatrixColorFilter)return shiraz.runtime.NativeBitmap.filterColorMatrix(color,shirazMatrix(value));
        return value == null ? color : shiraz.runtime.NativeBitmap.filterColor(shirazColor(value),color,shirazMode(value));
    }
}
