// SPDX-License-Identifier: GPL-3.0-only
package android.icu.number;

/** Zero-fill and truncation settings for the integer part of a formatted number. */
public class IntegerWidth {
    final int mMinInt;
    final int mMaxInt;

    private IntegerWidth(int minInt, int maxInt) {
        mMinInt = minInt;
        mMaxInt = maxInt;
    }

    public static IntegerWidth zeroFillTo(int minInt) {
        if (minInt < 0 || minInt > 999) {
            throw new IllegalArgumentException("Integer digits must be between 0 and 999 (inclusive)");
        }
        return new IntegerWidth(minInt, -1);
    }

    public IntegerWidth truncateAt(int maxInt) {
        if (maxInt < -1 || maxInt > 999 || (maxInt >= 0 && maxInt < mMinInt)) {
            throw new IllegalArgumentException("Invalid truncateAt: " + maxInt);
        }
        return new IntegerWidth(mMinInt, maxInt);
    }
}
