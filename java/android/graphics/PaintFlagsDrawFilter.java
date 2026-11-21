// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;

public class PaintFlagsDrawFilter extends DrawFilter {
    final int mClearBits;
    final int mSetBits;

    public PaintFlagsDrawFilter(int clearBits, int setBits) {
        mClearBits = clearBits;
        mSetBits = setBits;
    }
}
