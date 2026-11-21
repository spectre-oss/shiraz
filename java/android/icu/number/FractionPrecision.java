// SPDX-License-Identifier: GPL-3.0-only
package android.icu.number;

/** Precision subclass for fraction-based rounding strategies. */
public abstract class FractionPrecision extends Precision {
    FractionPrecision() {
    }

    public Precision withSignificantDigits(
            int minSignificantDigits,
            int maxSignificantDigits,
            NumberFormatter.RoundingPriority priority) {
        if (minSignificantDigits < 1 || maxSignificantDigits < minSignificantDigits || maxSignificantDigits > 999) {
            throw new IllegalArgumentException("Invalid significant digits");
        }
        PrecisionImpl p = new PrecisionImpl();
        p.mMinFrac = mMinFrac;
        p.mMaxFrac = mMaxFrac;
        p.mMinSig = minSignificantDigits;
        p.mMaxSig = maxSignificantDigits;
        return p;
    }

    public Precision withMinDigits(int minSignificantDigits) {
        return withSignificantDigits(1, minSignificantDigits, NumberFormatter.RoundingPriority.RELAXED);
    }

    public Precision withMaxDigits(int maxSignificantDigits) {
        return withSignificantDigits(1, maxSignificantDigits, NumberFormatter.RoundingPriority.STRICT);
    }
}
