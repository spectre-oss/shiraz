// SPDX-License-Identifier: GPL-3.0-only
package android.text.style;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.Spanned;

/** Paragraph leading-margin span that draws a Drawable alongside the paragraph. */
public class DrawableMarginSpan implements LeadingMarginSpan, LineHeightSpan {
    private final Drawable mDrawable;
    private final int mPad;

    public DrawableMarginSpan(Drawable drawable) {
        this(drawable, 0);
    }

    public DrawableMarginSpan(Drawable drawable, int pad) {
        mDrawable = drawable;
        mPad = pad;
    }

    @Override
    public int getLeadingMargin(boolean first) {
        return (mDrawable != null ? mDrawable.getIntrinsicWidth() : 0) + mPad;
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
        if (mDrawable == null || !(text instanceof Spanned)) {
            return;
        }
        int st = ((Spanned) text).getSpanStart(this);
        if (st != start) {
            return;
        }
        int dw = mDrawable.getIntrinsicWidth();
        int dh = mDrawable.getIntrinsicHeight();
        int ix = dir < 0 ? x - dw : x;
        int itop = layout != null ? layout.getLineTop(layout.getLineForOffset(st)) : top;
        mDrawable.setBounds(ix, itop, ix + dw, itop + dh);
        mDrawable.draw(c);
    }

    @Override
    public void chooseHeight(
            CharSequence text,
            int start,
            int end,
            int istartv,
            int v,
            Paint.FontMetricsInt fm) {
        if (mDrawable == null || !(text instanceof Spanned)) {
            return;
        }
        if (end == ((Spanned) text).getSpanEnd(this)) {
            int ht = mDrawable.getIntrinsicHeight();
            int need = ht - (v + fm.descent - fm.ascent - istartv);
            if (need > 0) {
                fm.descent += need;
            }
            need = ht - (v + fm.bottom - fm.top - istartv);
            if (need > 0) {
                fm.bottom += need;
            }
        }
    }

    @Override
    public String toString() {
        return "DrawableMarginSpan{drawable=" + mDrawable + ", padding=" + mPad + '}';
    }

    public Drawable getDrawable() {
        return mDrawable;
    }

    public int getPadding() {
        return mPad;
    }
}
