// SPDX-License-Identifier: GPL-3.0-only
package android.text.style;

/** Paragraph-level tab stop position contract and Standard implementation. */
public interface TabStopSpan extends ParagraphStyle {
    int getTabStop();

    public static class Standard implements TabStopSpan {
        private final int mOffset;

        public Standard(int offset) {
            mOffset = offset;
        }

        @Override
        public int getTabStop() {
            return mOffset;
        }

        @Override
        public String toString() {
            return "TabStopSpan.Standard{offset=" + getTabStop() + '}';
        }
    }
}
