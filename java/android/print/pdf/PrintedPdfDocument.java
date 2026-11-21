// SPDX-License-Identifier: GPL-3.0-only
package android.print.pdf;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.pdf.PdfDocument;
import android.print.PrintAttributes;
import android.print.PrintAttributes.Margins;
import android.print.PrintAttributes.MediaSize;
import java.util.Objects;

public class PrintedPdfDocument extends PdfDocument {
    private static final int MILS_PER_INCH = 1000;
    private static final int POINTS_IN_INCH = 72;

    private final int mPageWidth;
    private final int mPageHeight;
    private final Rect mContentRect;

    public PrintedPdfDocument(Context context, PrintAttributes attributes) {
        Objects.requireNonNull(attributes, "attributes cannot be null");
        MediaSize mediaSize = Objects.requireNonNull(
                attributes.getMediaSize(), "MediaSize cannot be null");
        this.mPageWidth = (int) (((float) mediaSize.getWidthMils() / MILS_PER_INCH)
                * POINTS_IN_INCH);
        this.mPageHeight = (int) (((float) mediaSize.getHeightMils() / MILS_PER_INCH)
                * POINTS_IN_INCH);

        Margins minMargins = attributes.getMinMargins();
        if (minMargins == null) {
            minMargins = Margins.NO_MARGINS;
        }
        int marginLeft = (int) (((float) minMargins.getLeftMils() / MILS_PER_INCH)
                * POINTS_IN_INCH);
        int marginTop = (int) (((float) minMargins.getTopMils() / MILS_PER_INCH)
                * POINTS_IN_INCH);
        int marginRight = (int) (((float) minMargins.getRightMils() / MILS_PER_INCH)
                * POINTS_IN_INCH);
        int marginBottom = (int) (((float) minMargins.getBottomMils() / MILS_PER_INCH)
                * POINTS_IN_INCH);
        this.mContentRect = new Rect(
                marginLeft,
                marginTop,
                mPageWidth - marginRight,
                mPageHeight - marginBottom);
    }

    public Page startPage(int pageNumber) {
        PageInfo pageInfo = new PageInfo.Builder(mPageWidth, mPageHeight, pageNumber)
                .setContentRect(mContentRect)
                .create();
        return startPage(pageInfo);
    }

    public int getPageWidth() {
        return mPageWidth;
    }

    public int getPageHeight() {
        return mPageHeight;
    }

    public Rect getPageContentRect() {
        return mContentRect;
    }
}
