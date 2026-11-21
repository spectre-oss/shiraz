// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import android.graphics.Rect;

public final class TimedText {
    private final Rect mTextBounds;
    private final String mTextChars;

    TimedText(Rect bounds, String text) {
        mTextBounds = bounds != null ? new Rect(bounds) : null;
        mTextChars = text;
    }

    public String getText() {
        return mTextChars;
    }

    public Rect getBounds() {
        return mTextBounds != null ? new Rect(mTextBounds) : null;
    }
}
