// SPDX-License-Identifier: GPL-3.0-only
package android.util;
public class TypedValue {
    public static final int TYPE_NULL = 0, TYPE_REFERENCE = 1, TYPE_ATTRIBUTE = 2, TYPE_STRING = 3, TYPE_FLOAT = 4,
        TYPE_DIMENSION = 5, TYPE_FRACTION = 6, TYPE_FIRST_INT = 16, TYPE_INT_DEC = 16, TYPE_INT_HEX = 17, TYPE_INT_BOOLEAN = 18,
        TYPE_FIRST_COLOR_INT = 28, TYPE_INT_COLOR_ARGB8 = 28, TYPE_INT_COLOR_RGB8 = 29, TYPE_INT_COLOR_ARGB4 = 30, TYPE_INT_COLOR_RGB4 = 31, TYPE_LAST_COLOR_INT = 31, TYPE_LAST_INT = 31;
    public static final int DATA_NULL_UNDEFINED=0, DATA_NULL_EMPTY=1, DENSITY_DEFAULT=0, DENSITY_NONE=65535;
    public static final int COMPLEX_UNIT_PX=0, COMPLEX_UNIT_DIP=1, COMPLEX_UNIT_SP=2, COMPLEX_UNIT_PT=3, COMPLEX_UNIT_IN=4, COMPLEX_UNIT_MM=5,
        COMPLEX_UNIT_FRACTION=0, COMPLEX_UNIT_FRACTION_PARENT=1, COMPLEX_UNIT_SHIFT=0, COMPLEX_UNIT_MASK=15,
        COMPLEX_RADIX_23p0=0, COMPLEX_RADIX_16p7=1, COMPLEX_RADIX_8p15=2, COMPLEX_RADIX_0p23=3, COMPLEX_RADIX_SHIFT=4, COMPLEX_RADIX_MASK=3,
        COMPLEX_MANTISSA_SHIFT=8, COMPLEX_MANTISSA_MASK=0xffffff;
    public int type, data, assetCookie, resourceId, changingConfigurations, density;
    public CharSequence string;
    public TypedValue() { }
    public boolean isColorType() { return type >= TYPE_FIRST_COLOR_INT && type <= TYPE_LAST_COLOR_INT; }
    private static final float[] RADIX_SCALE={1f/256,1f/32768,1f/8388608,1f/2147483648f};
    public static float complexToFloat(int value) { return (value & 0xffffff00)*RADIX_SCALE[(value >>> 4)&3]; }
    public int getComplexUnit() { return data & COMPLEX_UNIT_MASK; }
    public static float applyDimension(int unit,float value,DisplayMetrics metrics) {
        if (metrics == null) throw new NullPointerException("display metrics");
        switch (unit) {
            case COMPLEX_UNIT_PX: return value;
            case COMPLEX_UNIT_DIP: return value*metrics.density;
            case COMPLEX_UNIT_SP: return value*metrics.scaledDensity;
            case COMPLEX_UNIT_PT: return value*metrics.xdpi/72;
            case COMPLEX_UNIT_IN: return value*metrics.xdpi;
            case COMPLEX_UNIT_MM: return value*metrics.xdpi/25.4f;
            default: throw new IllegalArgumentException("Invalid dimension unit");
        }
    }
    public static float complexToDimension(int value,DisplayMetrics metrics) { return applyDimension(value&15,complexToFloat(value),metrics); }
    public static int complexToDimensionPixelOffset(int value,DisplayMetrics metrics) { return (int)complexToDimension(value,metrics); }
    public static int complexToDimensionPixelSize(int value,DisplayMetrics metrics) {
        float pixels=complexToDimension(value,metrics); int rounded=(int)(pixels >= 0 ? pixels+.5f : pixels-.5f);
        if (rounded != 0 || complexToFloat(value) == 0) return rounded;
        return complexToFloat(value) > 0 ? 1 : -1;
    }
    public float getDimension(DisplayMetrics metrics) { return complexToDimension(data,metrics); }
    public static float complexToFraction(int value,float base,float parentBase) {
        int unit=value&15; if (unit != COMPLEX_UNIT_FRACTION && unit != COMPLEX_UNIT_FRACTION_PARENT) throw new IllegalArgumentException("Invalid fraction unit");
        return complexToFloat(value)*(unit == COMPLEX_UNIT_FRACTION ? base : parentBase);
    }
    public float getFraction(float base,float parentBase) { return complexToFraction(data,base,parentBase); }
    public float getFloat() { return Float.intBitsToFloat(data); }
    public void setTo(TypedValue other) {
        type = other.type; data = other.data; assetCookie = other.assetCookie; resourceId = other.resourceId;
        changingConfigurations = other.changingConfigurations; density = other.density; string = other.string;
    }
    public CharSequence coerceToString() { return type == TYPE_STRING ? string : coerceToString(type, data); }
    public static String coerceToString(int type, int data) {
        if (type == TYPE_NULL || type == TYPE_STRING) return null;
        return android.content.res.Resources.shirazCoerce(type, data);
    }
}
