// SPDX-License-Identifier: GPL-3.0-only
package android.text;

/** CharSequence wrapper that overlays a temporary character array slice onto a source sequence. */
@Deprecated
public class AlteredCharSequence implements CharSequence, GetChars {
    private final int mStart;
    private final int mEnd;
    private final char[] mChars;
    private final CharSequence mSource;

    private AlteredCharSequence(CharSequence source, char[] sub, int substart, int subend) {
        mSource = source;
        mChars = sub;
        mStart = substart;
        mEnd = subend;
    }

    @Deprecated
    public static AlteredCharSequence make(
            CharSequence source, char[] sub, int substart, int subend) {
        if (source instanceof Spanned) {
            return new AlteredSpanned(source, sub, substart, subend);
        }
        return new AlteredCharSequence(source, sub, substart, subend);
    }

    private static class AlteredSpanned extends AlteredCharSequence implements Spanned {
        private final Spanned mSpanned;

        private AlteredSpanned(CharSequence source, char[] sub, int substart, int subend) {
            super(source, sub, substart, subend);
            mSpanned = (Spanned) source;
        }

        @Override
        public <T> T[] getSpans(int start, int end, Class<T> kind) {
            return mSpanned.getSpans(start, end, kind);
        }

        @Override
        public int getSpanStart(Object span) {
            return mSpanned.getSpanStart(span);
        }

        @Override
        public int getSpanEnd(Object span) {
            return mSpanned.getSpanEnd(span);
        }

        @Override
        public int getSpanFlags(Object span) {
            return mSpanned.getSpanFlags(span);
        }

        @Override
        public int nextSpanTransition(int start, int end, Class kind) {
            return mSpanned.nextSpanTransition(start, end, kind);
        }
    }

    @Override
    public char charAt(int off) {
        if (off >= mStart && off < mEnd) {
            return mChars[off - mStart];
        }
        return mSource.charAt(off);
    }

    @Override
    public int length() {
        return mSource.length();
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return make(
                mSource.subSequence(start, end), mChars, mStart - start, mEnd - start);
    }

    @Override
    public void getChars(int start, int end, char[] dest, int off) {
        TextUtils.getChars(mSource, start, end, dest, off);
        int a = Math.max(mStart, start);
        int b = Math.min(mEnd, end);
        if (a < b) {
            System.arraycopy(mChars, a - mStart, dest, off + (a - start), b - a);
        }
    }

    @Override
    public String toString() {
        int len = length();
        char[] ret = new char[len];
        getChars(0, len, ret, 0);
        return new String(ret);
    }
}
