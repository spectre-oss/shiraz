// SPDX-License-Identifier: GPL-3.0-only
package android.util;

/**
 * IEEE 754-2008 binary16 half-precision floating-point wrapper and bit-level conversion methods.
 */
public final class Half extends Number implements Comparable<Half> {
    private static final long serialVersionUID = 1L;

    public static final int SIZE = 16;
    public static final short EPSILON = (short) 0x1400;
    public static final int MAX_EXPONENT = 15;
    public static final int MIN_EXPONENT = -14;
    public static final short MAX_VALUE = (short) 0x7bff;
    public static final short MIN_NORMAL = (short) 0x0400;
    public static final short MIN_VALUE = (short) 0x0001;
    public static final short LOWEST_VALUE = (short) 0xfbff;
    public static final short NaN = (short) 0x7e00;
    public static final short NEGATIVE_INFINITY = (short) 0xfc00;
    public static final short NEGATIVE_ZERO = (short) 0x8000;
    public static final short POSITIVE_INFINITY = (short) 0x7c00;
    public static final short POSITIVE_ZERO = (short) 0x0000;

    private final short mValue;

    public Half(short value) {
        mValue = value;
    }

    public Half(float value) {
        mValue = toHalf(value);
    }

    public Half(double value) {
        mValue = toHalf((float) value);
    }

    public Half(String value) throws NumberFormatException {
        mValue = toHalf(Float.parseFloat(value));
    }

    public short halfValue() {
        return mValue;
    }

    @Override
    public byte byteValue() {
        return (byte) toFloat(mValue);
    }

    @Override
    public short shortValue() {
        return (short) toFloat(mValue);
    }

    @Override
    public int intValue() {
        return (int) toFloat(mValue);
    }

    @Override
    public long longValue() {
        return (long) toFloat(mValue);
    }

    @Override
    public float floatValue() {
        return toFloat(mValue);
    }

    @Override
    public double doubleValue() {
        return toFloat(mValue);
    }

    public boolean isNaN() {
        return isNaN(mValue);
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof Half) && (halfToIntBits(((Half) o).mValue) == halfToIntBits(mValue));
    }

    @Override
    public int hashCode() {
        return hashCode(mValue);
    }

    @Override
    public String toString() {
        return toString(mValue);
    }

    @Override
    public int compareTo(Half h) {
        return compare(mValue, h.mValue);
    }

    public static int hashCode(short h) {
        return halfToIntBits(h);
    }

    public static int compare(short x, short y) {
        if (less(x, y)) {
            return -1;
        }
        if (greater(x, y)) {
            return 1;
        }
        short xBits = isNaN(x) ? NaN : x;
        short yBits = isNaN(y) ? NaN : y;
        return xBits == yBits ? 0 : (xBits < yBits ? -1 : 1);
    }

    public static short halfToShortBits(short h) {
        return (h & 0x7fff) > 0x7c00 ? NaN : h;
    }

    public static int halfToIntBits(short h) {
        return (h & 0x7fff) > 0x7c00 ? 0x7e00 : (h & 0xffff);
    }

    public static int halfToRawIntBits(short h) {
        return h & 0xffff;
    }

    public static short intBitsToHalf(int bits) {
        return (short) (bits & 0xffff);
    }

    public static short copySign(short magnitude, short sign) {
        return (short) ((sign & 0x8000) | (magnitude & 0x7fff));
    }

    public static short abs(short h) {
        return (short) (h & 0x7fff);
    }

    public static short round(short h) {
        if (isNaN(h)) {
            return NaN;
        }
        if (isInfinite(h) || (h & 0x7fff) == 0) {
            return h;
        }
        float f = toFloat(h);
        float rounded = f >= 0.0f ? (float) Math.floor(f + 0.5d) : (float) Math.ceil(f - 0.5d);
        if (rounded == 0.0f && (h & 0x8000) != 0) {
            return NEGATIVE_ZERO;
        }
        return toHalf(rounded);
    }

    public static short ceil(short h) {
        if (isNaN(h)) {
            return NaN;
        }
        if (isInfinite(h) || (h & 0x7fff) == 0) {
            return h;
        }
        double c = Math.ceil(toFloat(h));
        if (c == 0.0d && (h & 0x8000) != 0) {
            return NEGATIVE_ZERO;
        }
        return toHalf((float) c);
    }

    public static short floor(short h) {
        if (isNaN(h)) {
            return NaN;
        }
        if (isInfinite(h) || (h & 0x7fff) == 0) {
            return h;
        }
        double fl = Math.floor(toFloat(h));
        if (fl == 0.0d && (h & 0x8000) != 0) {
            return NEGATIVE_ZERO;
        }
        return toHalf((float) fl);
    }

    public static short trunc(short h) {
        if (isNaN(h)) {
            return NaN;
        }
        if (isInfinite(h) || (h & 0x7fff) == 0) {
            return h;
        }
        float f = toFloat(h);
        double tr = f < 0.0f ? Math.ceil(f) : Math.floor(f);
        if (tr == 0.0d && (h & 0x8000) != 0) {
            return NEGATIVE_ZERO;
        }
        return toHalf((float) tr);
    }

    public static short min(short x, short y) {
        if (isNaN(x) || isNaN(y)) {
            return NaN;
        }
        if ((x & 0x7fff) == 0 && (y & 0x7fff) == 0) {
            return (x & 0x8000) != 0 ? x : y;
        }
        return less(x, y) ? x : y;
    }

    public static short max(short x, short y) {
        if (isNaN(x) || isNaN(y)) {
            return NaN;
        }
        if ((x & 0x7fff) == 0 && (y & 0x7fff) == 0) {
            return (x & 0x8000) != 0 ? y : x;
        }
        return greater(x, y) ? x : y;
    }

    public static boolean less(short x, short y) {
        if (isNaN(x) || isNaN(y)) {
            return false;
        }
        return toFloat(x) < toFloat(y);
    }

    public static boolean lessEquals(short x, short y) {
        if (isNaN(x) || isNaN(y)) {
            return false;
        }
        return toFloat(x) <= toFloat(y);
    }

    public static boolean greater(short x, short y) {
        if (isNaN(x) || isNaN(y)) {
            return false;
        }
        return toFloat(x) > toFloat(y);
    }

    public static boolean greaterEquals(short x, short y) {
        if (isNaN(x) || isNaN(y)) {
            return false;
        }
        return toFloat(x) >= toFloat(y);
    }

    public static boolean equals(short x, short y) {
        if (isNaN(x) || isNaN(y)) {
            return false;
        }
        return ((x | y) & 0x7fff) == 0 || x == y;
    }

    public static int getSign(short h) {
        return (h & 0x8000) == 0 ? 1 : -1;
    }

    public static int getExponent(short h) {
        return ((h >>> 10) & 0x1f) - 15;
    }

    public static int getSignificand(short h) {
        return h & 0x03ff;
    }

    public static boolean isInfinite(short h) {
        return (h & 0x7fff) == 0x7c00;
    }

    public static boolean isNaN(short h) {
        return (h & 0x7fff) > 0x7c00;
    }

    public static boolean isNormalized(short h) {
        int exp = h & 0x7c00;
        return exp != 0 && exp != 0x7c00;
    }

    public static float toFloat(short h) {
        int bits = h & 0xffff;
        int sign = (bits & 0x8000) << 16;
        int exp = (bits >>> 10) & 0x1f;
        int sig = bits & 0x03ff;
        if (exp == 0) {
            if (sig == 0) {
                return Float.intBitsToFloat(sign);
            }
            int outExp = 127 - 14;
            while ((sig & 0x0400) == 0) {
                sig <<= 1;
                outExp--;
            }
            sig &= 0x03ff;
            return Float.intBitsToFloat(sign | (outExp << 23) | (sig << 13));
        }
        if (exp == 31) {
            if (sig == 0) {
                return Float.intBitsToFloat(sign | 0x7f800000);
            }
            return Float.intBitsToFloat(sign | 0x7fc00000 | (sig << 13));
        }
        int outExp = exp - 15 + 127;
        return Float.intBitsToFloat(sign | (outExp << 23) | (sig << 13));
    }

    public static short toHalf(float f) {
        int bits = Float.floatToRawIntBits(f);
        int sign = (bits >>> 16) & 0x8000;
        int exp = (bits >>> 23) & 0xff;
        int sig = bits & 0x7fffff;
        if (exp == 0xff) {
            if (sig == 0) {
                return (short) (sign | 0x7c00);
            }
            int payload = sig >>> 13;
            return (short) (sign | 0x7c00 | payload | (payload == 0 ? 0x0200 : 0));
        }
        int uexp = exp - 127;
        if (uexp > 15) {
            return (short) (sign | 0x7c00);
        }
        if (uexp < -25) {
            return (short) sign;
        }
        if (uexp < -14) {
            int mant = sig | 0x800000;
            int shift = -14 - uexp + 13;
            int halfSub = mant >>> shift;
            int remMask = (1 << shift) - 1;
            int halfway = 1 << (shift - 1);
            int rem = mant & remMask;
            if (rem > halfway || (rem == halfway && (halfSub & 1) != 0)) {
                halfSub++;
            }
            return (short) (sign | halfSub);
        }
        int halfExp = (uexp + 15) << 10;
        int halfSig = sig >>> 13;
        int rem = sig & 0x1fff;
        if (rem > 0x1000 || (rem == 0x1000 && (halfSig & 1) != 0)) {
            halfSig++;
            if (halfSig == 0x0400) {
                halfSig = 0;
                halfExp += 0x0400;
            }
        }
        return (short) (sign | halfExp | halfSig);
    }

    public static Half valueOf(short h) {
        return new Half(h);
    }

    public static Half valueOf(float f) {
        return new Half(f);
    }

    public static Half valueOf(String s) {
        return new Half(s);
    }

    public static short parseHalf(String s) throws NumberFormatException {
        if (s == null) {
            throw new NullPointerException("s == null");
        }
        return toHalf(Float.parseFloat(s));
    }

    public static String toString(short h) {
        return Float.toString(toFloat(h));
    }

    public static String toHexString(short h) {
        if (isNaN(h)) {
            return "NaN";
        }
        if (h == POSITIVE_INFINITY) {
            return "Infinity";
        }
        if (h == NEGATIVE_INFINITY) {
            return "-Infinity";
        }
        StringBuilder out = new StringBuilder();
        if ((h & 0x8000) != 0) {
            out.append('-');
        }
        int exp = (h >>> 10) & 0x1f;
        int sig = h & 0x03ff;
        if (exp == 0) {
            if (sig == 0) {
                out.append("0x0.0p0");
                return out.toString();
            }
            out.append("0x0.");
            String hex = Integer.toHexString(sig << 2);
            for (int pad = 3 - hex.length(); pad > 0; pad--) {
                out.append('0');
            }
            out.append(stripTrailingZeros(hex)).append("p-14");
            return out.toString();
        }
        out.append("0x1.");
        if (sig == 0) {
            out.append('0');
        } else {
            String hex = Integer.toHexString(sig << 2);
            for (int pad = 3 - hex.length(); pad > 0; pad--) {
                out.append('0');
            }
            out.append(stripTrailingZeros(hex));
        }
        out.append('p').append(exp - 15);
        return out.toString();
    }

    private static String stripTrailingZeros(String hex) {
        int end = hex.length();
        while (end > 1 && hex.charAt(end - 1) == '0') {
            end--;
        }
        return hex.substring(0, end);
    }
}
