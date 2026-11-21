// SPDX-License-Identifier: GPL-3.0-only
package android.text.style;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.text.Spanned;

/** Paragraph leading-margin span that draws a Bitmap icon alongside the paragraph. */
public class IconMarginSpan implements LeadingMarginSpan, LineHeightSpan {
    private final Bitmap mBitmap;
    private final int mPad;

    public IconMarginSpan(Bitmap bitmap) {
        this(bitmap, 0);
    }

    public IconMarginSpan(Bitmap bitmap, int pad) {
        mBitmap = bitmap;
        mPad = pad;
    }

    @Override
    public int getLeadingMargin(boolean first) {
        return (mBitmap != null ? mBitmap.getWidth() : 0) + mPad;
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
        if (mBitmap == null || !(text instanceof Spanned)) {
            return;
        }
        int st = ((Spanned) text).getSpanStart(this);
        if (st != start) {
            return;
        }
        int itop = layout != null ? layout.getLineTop(layout.getLineForOffset(st)) : top;
        int ix = dir < 0 ? x - mBitmap.getWidth() : x;
        c.drawBitmap(mBitmap, ix, itop, p);
    }

    @Override
    public void chooseHeight(
            CharSequence text,
            int start,
            int end,
            int istartv,
            int v,
            Paint.FontMetricsInt fm) {
        if (mBitmap == null || !(text instanceof Spanned)) {
            return;
        }
        if (end == ((Spanned) text).getSpanEnd(this)) {
            int ht = mBitmap.getHeight();
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
        return "IconMarginSpan{bitmap=" + mBitmap + ", padding=" + mPad + '}';
    }

    public Bitmap getBitmap() {
        return mBitmap;
    }

    public int getPadding() {
        return mPad;
    }
}
