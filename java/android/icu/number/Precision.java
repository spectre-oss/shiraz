// SPDX-License-Identifier: GPL-3.0-only
package android.icu.number;

import android.icu.util.Currency;
import java.math.BigDecimal;

/** Defines rounding precision rules for NumberFormatter. */
public abstract class Precision {
    int mMinFrac = -1;
    int mMaxFrac = -1;
    int mMinSig = -1;
    int mMaxSig = -1;
    BigDecimal mIncrement;
    NumberFormatter.TrailingZeroDisplay mTrailingZeroDisplay =
            NumberFormatter.TrailingZeroDisplay.AUTO;

    Precision() {
    }

    public static Precision unlimited() {
        PrecisionImpl p = new PrecisionImpl();
        p.mMinFrac = 0;
        p.mMaxFrac = 999;
        return p;
    }

    public static FractionPrecision integer() {
        return minMaxFraction(0, 0);
    }

    public static FractionPrecision fixedFraction(int minMaxFractionPlaces) {
        return minMaxFraction(minMaxFractionPlaces, minMaxFractionPlaces);
    }

    public static FractionPrecision minFraction(int minFractionPlaces) {
        return minMaxFraction(minFractionPlaces, 999);
    }

    public static FractionPrecision maxFraction(int maxFractionPlaces) {
        return minMaxFraction(0, maxFractionPlaces);
    }

    public static FractionPrecision minMaxFraction(int minFractionPlaces, int maxFractionPlaces) {
        if (minFractionPlaces < 0 || maxFractionPlaces < minFractionPlaces || maxFractionPlaces > 999) {
            throw new IllegalArgumentException("Invalid fraction places");
        }
        return new FractionPrecisionImpl(minFractionPlaces, maxFractionPlaces);
    }

    public static Precision fixedSignificantDigits(int minMaxSignificantDigits) {
        return minMaxSignificantDigits(minMaxSignificantDigits, minMaxSignificantDigits);
    }

    public static Precision minSignificantDigits(int minSignificantDigits) {
        return minMaxSignificantDigits(minSignificantDigits, 999);
    }

    public static Precision maxSignificantDigits(int maxSignificantDigits) {
        return minMaxSignificantDigits(1, maxSignificantDigits);
    }

    public static Precision minMaxSignificantDigits(int minSignificantDigits, int maxSignificantDigits) {
        if (minSignificantDigits < 1 || maxSignificantDigits < minSignificantDigits || maxSignificantDigits > 999) {
            throw new IllegalArgumentException("Invalid significant digits");
        }
        PrecisionImpl p = new PrecisionImpl();
        p.mMinSig = minSignificantDigits;
        p.mMaxSig = maxSignificantDigits;
        return p;
    }

    public static Precision increment(BigDecimal roundingIncrement) {
        if (roundingIncrement == null || roundingIncrement.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Rounding increment must be positive and non-null");
        }
        PrecisionImpl p = new PrecisionImpl();
        p.mIncrement = roundingIncrement;
        p.mMinFrac = Math.max(0, roundingIncrement.scale());
        p.mMaxFrac = Math.max(0, roundingIncrement.scale());
        return p;
    }

    public static CurrencyPrecision currency(Currency.CurrencyUsage currencyUsage) {
        if (currencyUsage == null) {
            throw new IllegalArgumentException("CurrencyUsage must be non-null");
        }
        return new CurrencyPrecisionImpl(currencyUsage);
    }

    public Precision trailingZeroDisplay(NumberFormatter.TrailingZeroDisplay trailingZeroDisplay) {
        PrecisionImpl copy = new PrecisionImpl();
        copy.mMinFrac = mMinFrac;
        copy.mMaxFrac = mMaxFrac;
        copy.mMinSig = mMinSig;
        copy.mMaxSig = mMaxSig;
        copy.mIncrement = mIncrement;
        copy.mTrailingZeroDisplay = trailingZeroDisplay;
        return copy;
    }

    static final class PrecisionImpl extends Precision {
    }

    static final class FractionPrecisionImpl extends FractionPrecision {
        FractionPrecisionImpl(int minFrac, int maxFrac) {
            mMinFrac = minFrac;
            mMaxFrac = maxFrac;
        }
    }

    static final class CurrencyPrecisionImpl extends CurrencyPrecision {
        final Currency.CurrencyUsage mUsage;

        CurrencyPrecisionImpl(Currency.CurrencyUsage usage) {
            mUsage = usage;
            mMinFrac = 2;
            mMaxFrac = 2;
        }
    }
}
