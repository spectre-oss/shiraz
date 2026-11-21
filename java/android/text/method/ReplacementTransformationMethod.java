// SPDX-License-Identifier: GPL-3.0-only
package android.text.method;

import android.graphics.Rect;
import android.text.Editable;
import android.text.GetChars;
import android.text.Spannable;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.TextUtils;
import android.view.View;

/** Base TransformationMethod that substitutes specific characters one-for-one. */
public abstract class ReplacementTransformationMethod implements TransformationMethod {
    public ReplacementTransformationMethod() {
    }

    protected abstract char[] getOriginal();

    protected abstract char[] getReplacement();

    @Override
    public CharSequence getTransformation(CharSequence source, View v) {
        char[] original = getOriginal();
        char[] replacement = getReplacement();
        if (!(source instanceof Editable)) {
            boolean doNothing = true;
            int n = original.length;
            for (int i = 0; i < n; i++) {
                if (TextUtils.indexOf(source, original[i]) >= 0) {
                    doNothing = false;
                    break;
                }
            }
            if (doNothing) {
                return source;
            }
            if (!(source instanceof Spannable)) {
                if (source instanceof Spanned) {
                    return new SpannedString(
                            new SpannedReplacementCharSequence(
                                    (Spanned) source, original, replacement));
                }
                return new ReplacementCharSequence(source, original, replacement).toString();
            }
        }
        if (source instanceof Spanned) {
            return new SpannedReplacementCharSequence((Spanned) source, original, replacement);
        }
        return new ReplacementCharSequence(source, original, replacement);
    }

    @Override
    public void onFocusChanged(
            View view,
            CharSequence sourceText,
            boolean focused,
            int direction,
            Rect previouslyFocusedRect) {
    }

    private static class ReplacementCharSequence implements CharSequence, GetChars {
        private final char[] mOriginal;
        private final char[] mReplacement;
        private final CharSequence mSource;

        ReplacementCharSequence(CharSequence source, char[] original, char[] replacement) {
            mSource = source;
            mOriginal = original;
            mReplacement = replacement;
        }

        @Override
        public int length() {
            return mSource.length();
        }

        @Override
        public char charAt(int i) {
            char c = mSource.charAt(i);
            int n = mOriginal.length;
            for (int j = 0; j < n; j++) {
                if (c == mOriginal[j]) {
                    c = mReplacement[j];
                }
            }
            return c;
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            char[] c = new char[end - start];
            getChars(start, end, c, 0);
            return new String(c);
        }

        @Override
        public String toString() {
            char[] c = new char[length()];
            getChars(0, length(), c, 0);
            return new String(c);
        }

        @Override
        public void getChars(int start, int end, char[] dest, int off) {
            TextUtils.getChars(mSource, start, end, dest, off);
            int offend = end - start + off;
            int n = mOriginal.length;
            for (int i = off; i < offend; i++) {
                char c = dest[i];
                for (int j = 0; j < n; j++) {
                    if (c == mOriginal[j]) {
                        dest[i] = mReplacement[j];
                    }
                }
            }
        }
    }

    private static class SpannedReplacementCharSequence
            extends ReplacementCharSequence implements Spanned {
        private final Spanned mSpanned;

        SpannedReplacementCharSequence(Spanned source, char[] original, char[] replacement) {
            super(source, original, replacement);
            mSpanned = source;
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return new SpannedString(this).subSequence(start, end);
        }

        @Override
        public <T> T[] getSpans(int start, int end, Class<T> type) {
            return mSpanned.getSpans(start, end, type);
        }

        @Override
        public int getSpanStart(Object tag) {
            return mSpanned.getSpanStart(tag);
        }

        @Override
        public int getSpanEnd(Object tag) {
            return mSpanned.getSpanEnd(tag);
        }

        @Override
        public int getSpanFlags(Object tag) {
            return mSpanned.getSpanFlags(tag);
        }

        @Override
        public int nextSpanTransition(int start, int end, Class type) {
            return mSpanned.nextSpanTransition(start, end, type);
        }
    }
}
