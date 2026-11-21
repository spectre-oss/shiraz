// SPDX-License-Identifier: GPL-3.0-only
package android.text.style;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Parcel;
import android.text.Layout;
import android.text.ParcelableSpan;

/** Paragraph leading-margin span drawing a vertical quote stripe. */
public class QuoteSpan implements LeadingMarginSpan, ParcelableSpan {
    public static final int STANDARD_STRIPE_WIDTH_PX = 2;
    public static final int STANDARD_GAP_WIDTH_PX = 2;
    public static final int STANDARD_COLOR = 0xff0000ff;

    private final int mColor;
    private final int mStripeWidth;
    private final int mGapWidth;

    public QuoteSpan() {
        this(STANDARD_COLOR, STANDARD_STRIPE_WIDTH_PX, STANDARD_GAP_WIDTH_PX);
    }

    public QuoteSpan(int color) {
        this(color, STANDARD_STRIPE_WIDTH_PX, STANDARD_GAP_WIDTH_PX);
    }

    public QuoteSpan(int color, int stripeWidth, int gapWidth) {
        mColor = color;
        mStripeWidth = stripeWidth;
        mGapWidth = gapWidth;
    }

    public QuoteSpan(Parcel src) {
        mColor = src.readInt();
        mStripeWidth = src.readInt();
        mGapWidth = src.readInt();
    }

    @Override
    public int getSpanTypeId() {
        return 9;
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
        dest.writeInt(mStripeWidth);
        dest.writeInt(mGapWidth);
    }

    @Override
    public void writeToParcelInternal(Parcel dest, int flags) {
        writeToParcel(dest, flags);
    }

    public int getColor() {
        return mColor;
    }

    public int getStripeWidth() {
        return mStripeWidth;
    }

    public int getGapWidth() {
        return mGapWidth;
    }

    @Override
    public int getLeadingMargin(boolean first) {
        return mStripeWidth + mGapWidth;
    }

    @Override
    public void drawLeadingMargin(
            Canvas c,
            Paint p,
            int x,
            int dir,
            int top,
            int baseline,
            int bottom,
            CharSequence text,
            int start,
            int end,
            boolean first,
            Layout layout) {
        Paint.Style style = p.getStyle();
        int color = p.getColor();
        p.setStyle(Paint.Style.FILL);
        p.setColor(mColor);
        c.drawRect(x, top, x + dir * mStripeWidth, bottom, p);
        p.setStyle(style);
        p.setColor(color);
    }

    @Override
    public String toString() {
        return "QuoteSpan{color=" + String.format("#%08X", Integer.valueOf(getColor()))
                + ", stripeWidth=" + getStripeWidth()
                + ", gapWidth=" + getGapWidth() + '}';
    }
}
