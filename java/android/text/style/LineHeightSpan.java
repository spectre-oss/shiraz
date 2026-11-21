// SPDX-License-Identifier: GPL-3.0-only
package android.text.style;

import android.graphics.Paint;
import android.os.Parcel;
import android.text.ParcelableSpan;
import android.text.TextPaint;

/** Callback contract for adjusting a line's font metrics. */
public interface LineHeightSpan extends ParagraphStyle, WrapTogetherSpan {
    void chooseHeight(
            CharSequence text,
            int start,
            int end,
            int spanStartY,
            int lineY,
            Paint.FontMetricsInt metrics);

    public interface WithDensity extends LineHeightSpan {
        void chooseHeight(
                CharSequence text,
                int start,
                int end,
                int spanStartY,
                int lineY,
                Paint.FontMetricsInt metrics,
                TextPaint paint);
    }

    public static class Standard implements LineHeightSpan, ParcelableSpan {
        private final int mHeight;

        public Standard(int height) {
            if (height <= 0) {
                throw new IllegalArgumentException("Height:" + height + " must be positive");
            }
            mHeight = height;
        }

        public Standard(Parcel src) {
            mHeight = src.readInt();
        }

        public int getHeight() {
            return mHeight;
        }

        @Override
        public int getSpanTypeId() {
            return 28;
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
            dest.writeInt(mHeight);
        }

        @Override
        public void writeToParcelInternal(Parcel dest, int flags) {
            writeToParcel(dest, flags);
        }

        @Override
        public void chooseHeight(
                CharSequence text,
                int start,
                int end,
                int spanStartv,
                int lineHeight,
                Paint.FontMetricsInt fm) {
            final int originHeight = fm.descent - fm.ascent;
            if (originHeight <= 0) {
                return;
            }
            final float ratio = mHeight * 1.0f / originHeight;
            fm.descent = Math.round(fm.descent * ratio);
            fm.ascent = fm.descent - mHeight;
        }
    }
}
