// SPDX-License-Identifier: GPL-3.0-only
package android.icu.number;

/** Compact decimal notation (e.g. 1.2K or 1.2 thousand). */
public class CompactNotation extends Notation {
    final boolean mLongStyle;

    CompactNotation(boolean longStyle) {
        mLongStyle = longStyle;
    }
}
