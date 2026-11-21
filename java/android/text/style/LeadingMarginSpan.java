// SPDX-License-Identifier: GPL-3.0-only
package android.text.style;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Parcel;
import android.text.Layout;
import android.text.ParcelableSpan;

/** Paragraph margin contract; StaticLayout supports zero-width drawing callbacks. */
public interface LeadingMarginSpan extends ParagraphStyle {
    int getLeadingMargin(boolean first);

    void drawLeadingMargin(
            Canvas canvas,
            Paint paint,
            int x,
            int dir,
            int top,
            int baseline,
            int bottom,
            CharSequence text,
            int start,
            int end,
            boolean first,
            Layout layout);

    public interface LeadingMarginSpan2 extends LeadingMarginSpan, WrapTogetherSpan {
        int getLeadingMarginLineCount();
    }

    public class Standard implements LeadingMarginSpan, ParcelableSpan {
        private final int first;
        private final int rest;

        public Standard(int first, int rest) {
            this.first = first;
            this.rest = rest;
        }

        public Standard(int every) {
            this(every, every);
        }

        public Standard(Parcel src) {
            this.first = src.readInt();
            this.rest = src.readInt();
        }

        @Override
        public int getSpanTypeId() {
            return 10;
        }

        @Override
        public int getSpanTypeIdInternal() {
            return getSpanTypeId();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(first);
            dest.writeInt(rest);
        }

        @Override
        public void writeToParcelInternal(Parcel dest, int flags) {
            writeToParcel(dest, flags);
        }

        @Override
        public int getLeadingMargin(boolean isFirst) {
            return isFirst ? first : rest;
        }

        @Override
        public void drawLeadingMargin(
                Canvas canvas,
                Paint paint,
                int x,
                int dir,
                int top,
                int baseline,
                int bottom,
                CharSequence text,
                int start,
                int end,
                boolean isFirst,
                Layout layout) {
        }
    }
}
