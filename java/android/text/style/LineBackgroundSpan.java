// SPDX-License-Identifier: GPL-3.0-only
package android.text.style;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Parcel;
import android.text.ParcelableSpan;

/** Paragraph-level background drawing contract and solid-color Standard implementation. */
public interface LineBackgroundSpan extends ParagraphStyle {
    void drawBackground(
            Canvas canvas,
            Paint paint,
            int left,
            int right,
            int top,
            int baseline,
            int bottom,
            CharSequence text,
            int start,
            int end,
            int lineNumber);

    public static class Standard implements LineBackgroundSpan, ParcelableSpan {
        private final int mColor;

        public Standard(int color) {
            mColor = color;
        }

        public Standard(Parcel src) {
            mColor = src.readInt();
        }

        @Override
        public int getSpanTypeId() {
            return 27;
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
            dest.writeInt(mColor);
        }

        @Override
        public void writeToParcelInternal(Parcel dest, int flags) {
            writeToParcel(dest, flags);
        }

        public final int getColor() {
            return mColor;
        }

        @Override
        public void drawBackground(
                Canvas canvas,
                Paint paint,
                int left,
                int right,
                int top,
                int baseline,
                int bottom,
                CharSequence text,
                int start,
                int end,
                int lineNumber) {
            final int originColor = paint.getColor();
            paint.setColor(mColor);
            canvas.drawRect(left, top, right, bottom, paint);
            paint.setColor(originColor);
        }
    }
}
