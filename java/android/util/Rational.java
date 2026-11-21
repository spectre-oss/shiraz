// SPDX-License-Identifier: GPL-3.0-only
package android.util;

import java.util.Objects;

/**
 * Immutable rational number {@code numerator / denominator} reduced to lowest terms with IEEE-like
 * {@link #NaN}, {@link #POSITIVE_INFINITY}, {@link #NEGATIVE_INFINITY}, and {@link #ZERO} values.
 */
public final class Rational extends Number implements Comparable<Rational> {
    private static final long serialVersionUID = 1L;

    public static final Rational NaN = new Rational(0, 0);
    public static final Rational POSITIVE_INFINITY = new Rational(1, 0);
    public static final Rational NEGATIVE_INFINITY = new Rational(-1, 0);
    public static final Rational ZERO = new Rational(0, 1);

    private final int mNumerator;
    private final int mDenominator;

    public Rational(int numerator, int denominator) {
        if (denominator == 0) {
            mNumerator = Integer.compare(numerator, 0);
            mDenominator = 0;
            return;
        }
        if (numerator == 0) {
            mNumerator = 0;
            mDenominator = 1;
            return;
        }
        long num = numerator;
        long den = denominator;
        if (den < 0) {
            num = -num;
            den = -den;
        }
        long common = gcd(Math.abs(num), den);
        mNumerator = (int) (num / common);
        mDenominator = (int) (den / common);
    }

    private static long gcd(long a, long b) {
        while (b != 0) {
            long next = a % b;
            a = b;
            b = next;
        }
        return a == 0 ? 1 : a;
    }

    public int getNumerator() {
        return mNumerator;
    }

    public int getDenominator() {
        return mDenominator;
    }

    public boolean isNaN() {
        return mDenominator == 0 && mNumerator == 0;
    }

    public boolean isInfinite() {
        return mNumerator != 0 && mDenominator == 0;
    }

    public boolean isFinite() {
        return mDenominator != 0;
    }

    public boolean isZero() {
        return isFinite() && mNumerator == 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Rational)) {
            return false;
        }
        Rational other = (Rational) obj;
        return mNumerator == other.mNumerator && mDenominator == other.mDenominator;
    }

    @Override
    public String toString() {
        if (isNaN()) {
            return "NaN";
        }
        if (mDenominator == 0) {
            return mNumerator > 0 ? "Infinity" : "-Infinity";
        }
        return mNumerator + "/" + mDenominator;
    }

    @Override
    public int hashCode() {
        int numeratorFlipped = (mNumerator << 16) | (mNumerator >>> 16);
        return mDenominator ^ numeratorFlipped;
    }

    @Override
    public double doubleValue() {
        return (double) mNumerator / (double) mDenominator;
    }

    @Override
    public float floatValue() {
        return (float) mNumerator / (float) mDenominator;
    }

    @Override
    public int intValue() {
        if (mDenominator == 0) {
            return mNumerator > 0 ? Integer.MAX_VALUE : (mNumerator < 0 ? Integer.MIN_VALUE : 0);
        }
        return mNumerator / mDenominator;
    }

    @Override
    public long longValue() {
        if (mDenominator == 0) {
            return mNumerator > 0 ? Long.MAX_VALUE : (mNumerator < 0 ? Long.MIN_VALUE : 0L);
        }
        return mNumerator / mDenominator;
    }

    @Override
    public short shortValue() {
        return (short) intValue();
    }

    @Override
    public int compareTo(Rational another) {
        Objects.requireNonNull(another, "another must not be null");
        if (equals(another)) {
            return 0;
        }
        if (isNaN()) {
            return 1;
        }
        if (another.isNaN()) {
            return -1;
        }
        if (equals(POSITIVE_INFINITY) || another.equals(NEGATIVE_INFINITY)) {
            return 1;
        }
        if (equals(NEGATIVE_INFINITY) || another.equals(POSITIVE_INFINITY)) {
            return -1;
        }
        long lhs = ((long) mNumerator) * another.mDenominator;
        long rhs = ((long) another.mNumerator) * mDenominator;
        return Long.compare(lhs, rhs);
    }

    public static Rational parseRational(String string) throws NumberFormatException {
        Objects.requireNonNull(string, "string must not be null");
        if ("NaN".equals(string)) {
            return NaN;
        }
        if ("Infinity".equals(string)) {
            return POSITIVE_INFINITY;
        }
        if ("-Infinity".equals(string)) {
            return NEGATIVE_INFINITY;
        }
        int sep = string.indexOf(':');
        if (sep < 0) {
            sep = string.indexOf('/');
        }
        if (sep < 0) {
            throw new NumberFormatException("Invalid Rational: \"" + string + "\"");
        }
        try {
            int num = Integer.parseInt(string.substring(0, sep));
            int den = Integer.parseInt(string.substring(sep + 1));
            return new Rational(num, den);
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Invalid Rational: \"" + string + "\"");
        }
    }
}
