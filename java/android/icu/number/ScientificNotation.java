// SPDX-License-Identifier: GPL-3.0-only
package android.icu.number;

/** Scientific and engineering exponent notation settings. */
public class ScientificNotation extends Notation {
    final int mEngineeringInterval;
    final boolean mRequireMinInt;
    final int mMinExponentDigits;
    final NumberFormatter.SignDisplay mExponentSignDisplay;

    ScientificNotation(
            int engineeringInterval,
            boolean requireMinInt,
            int minExponentDigits,
            NumberFormatter.SignDisplay exponentSignDisplay) {
        mEngineeringInterval = engineeringInterval;
        mRequireMinInt = requireMinInt;
        mMinExponentDigits = minExponentDigits;
        mExponentSignDisplay = exponentSignDisplay;
    }

    public ScientificNotation withMinExponentDigits(int minExponentDigits) {
        if (minExponentDigits < 1 || minExponentDigits > 999) {
            throw new IllegalArgumentException("Exponent digits must be between 1 and 999 (inclusive)");
        }
        return new ScientificNotation(
                mEngineeringInterval, mRequireMinInt, minExponentDigits, mExponentSignDisplay);
    }

    public ScientificNotation withExponentSignDisplay(NumberFormatter.SignDisplay exponentSignDisplay) {
        return new ScientificNotation(
                mEngineeringInterval, mRequireMinInt, mMinExponentDigits, exponentSignDisplay);
    }
}
