// SPDX-License-Identifier: GPL-3.0-only
package android.icu.number;

/** Defines the notation style to be used when formatting numbers in NumberFormatter. */
public class Notation {
    Notation() {
    }

    public static ScientificNotation scientific() {
        return new ScientificNotation(1, false, 1, NumberFormatter.SignDisplay.AUTO);
    }

    public static ScientificNotation engineering() {
        return new ScientificNotation(3, false, 1, NumberFormatter.SignDisplay.AUTO);
    }

    public static CompactNotation compactShort() {
        return new CompactNotation(false);
    }

    public static CompactNotation compactLong() {
        return new CompactNotation(true);
    }

    public static SimpleNotation simple() {
        return SimpleNotation.INSTANCE;
    }
}
