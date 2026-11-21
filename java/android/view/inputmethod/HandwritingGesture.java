// SPDX-License-Identifier: GPL-3.0-only

package android.view.inputmethod;

public abstract class HandwritingGesture {
    public static final int GRANULARITY_WORD = 1;
    public static final int GRANULARITY_CHARACTER = 2;

    String mFallbackText;

    HandwritingGesture() {
    }

    public final String getFallbackText() {
        return mFallbackText;
    }
}
