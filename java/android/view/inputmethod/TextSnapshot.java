// SPDX-License-Identifier: GPL-3.0-only

package android.view.inputmethod;

import java.util.Objects;

public final class TextSnapshot {
    private final SurroundingText mSurroundingText;
    private final int mCompositionStart;
    private final int mCompositionEnd;
    private final int mCursorCapsMode;

    public TextSnapshot(
            SurroundingText surroundingText,
            int compositionStart,
            int compositionEnd,
            int cursorCapsMode) {
        Objects.requireNonNull(surroundingText, "surroundingText");
        if (compositionStart < -1) {
            throw new IllegalArgumentException("compositionStart must be -1 or higher");
        }
        if (compositionEnd < -1) {
            throw new IllegalArgumentException("compositionEnd must be -1 or higher");
        }
        if ((compositionStart == -1 && compositionEnd != -1)
                || (compositionStart != -1 && compositionEnd == -1)) {
            throw new IllegalArgumentException(
                    "compositionStart and compositionEnd must be both -1 or both >= 0");
        }
        if (compositionStart > compositionEnd) {
            throw new IllegalArgumentException("compositionStart=" + compositionStart
                    + " must be <= compositionEnd=" + compositionEnd);
        }
        mSurroundingText = surroundingText;
        mCompositionStart = compositionStart;
        mCompositionEnd = compositionEnd;
        mCursorCapsMode = cursorCapsMode;
    }

    public SurroundingText getSurroundingText() {
        return mSurroundingText;
    }

    public int getSelectionStart() {
        if (mSurroundingText.getOffset() < 0) {
            return -1;
        }
        return mSurroundingText.getSelectionStart() + mSurroundingText.getOffset();
    }

    public int getSelectionEnd() {
        if (mSurroundingText.getOffset() < 0) {
            return -1;
        }
        return mSurroundingText.getSelectionEnd() + mSurroundingText.getOffset();
    }

    public int getCompositionStart() {
        return mCompositionStart;
    }

    public int getCompositionEnd() {
        return mCompositionEnd;
    }

    public int getCursorCapsMode() {
        return mCursorCapsMode;
    }
}
