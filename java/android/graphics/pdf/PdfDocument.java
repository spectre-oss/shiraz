// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.pdf;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import dalvik.system.CloseGuard;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.zip.Deflater;

public class PdfDocument {
    private final CloseGuard mCloseGuard = CloseGuard.get();
    private final List<PageInfo> mPages = new ArrayList<PageInfo>();
    private final List<Bitmap> mPageBitmaps = new ArrayList<Bitmap>();
    private Page mCurrentPage;
    private boolean mClosed;

    public PdfDocument() {
        mCloseGuard.open("close");
    }

    public Page startPage(PageInfo pageInfo) {
        throwIfClosed();
        throwIfCurrentPageNotFinished();
        if (pageInfo == null) {
            throw new IllegalArgumentException("page cannot be null");
        }
        Bitmap bitmap = Bitmap.createBitmap(
                pageInfo.mPageWidth, pageInfo.mPageHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.clipRect(pageInfo.mContentRect);
        mCurrentPage = new Page(canvas, pageInfo, bitmap);
        return mCurrentPage;
    }

    public void finishPage(Page page) {
        throwIfClosed();
        if (page == null) {
            throw new IllegalArgumentException("page cannot be null");
        }
        if (page != mCurrentPage) {
            throw new IllegalStateException("Invalid page");
        }
        if (page.isFinished()) {
            throw new IllegalStateException("Page already finished");
        }
        mPages.add(page.getInfo());
        mPageBitmaps.add(page.mBitmap);
        page.finish();
        mCurrentPage = null;
    }

    public void writeTo(OutputStream out) throws IOException {
        throwIfClosed();
        throwIfCurrentPageNotFinished();
        if (out == null) {
            throw new IllegalArgumentException("out cannot be null!");
        }
        writePdfBinary(out, mPages, mPageBitmaps);
    }

    public List<PageInfo> getPages() {
        return Collections.unmodifiableList(new ArrayList<PageInfo>(mPages));
    }

    public void close() {
        throwIfCurrentPageNotFinished();
        dispose();
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            if (mCloseGuard != null) {
                mCloseGuard.warnIfOpen();
            }
            dispose();
        } finally {
            super.finalize();
        }
    }

    private void dispose() {
        if (!mClosed) {
            mClosed = true;
            mCloseGuard.close();
            for (int i = 0; i < mPageBitmaps.size(); i++) {
                Bitmap bmp = mPageBitmaps.get(i);
                if (bmp != null && !bmp.isRecycled()) {
                    bmp.recycle();
                }
            }
            mPageBitmaps.clear();
            mCurrentPage = null;
        }
    }

    private void throwIfClosed() {
        if (mClosed) {
            throw new IllegalStateException("document is closed!");
        }
    }

    private void throwIfCurrentPageNotFinished() {
        if (mCurrentPage != null) {
            throw new IllegalStateException("Current page not finished!");
        }
    }

    private static byte[] deflateBytes(byte[] raw) {
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION);
        try {
            deflater.setInput(raw);
            deflater.finish();
            ByteArrayOutputStream bos = new ByteArrayOutputStream(Math.max(64, raw.length / 4));
            byte[] buf = new byte[8192];
            while (!deflater.finished()) {
                int n = deflater.deflate(buf);
                if (n > 0) {
                    bos.write(buf, 0, n);
                } else if (deflater.needsInput()) {
                    break;
                }
            }
            return bos.toByteArray();
        } finally {
            deflater.end();
        }
    }

    private static final class CountingOut {
        private final OutputStream mOut;
        long mOffset;

        CountingOut(OutputStream out) {
            this.mOut = out;
        }

        void write(byte[] bytes) throws IOException {
            mOut.write(bytes);
            mOffset += bytes.length;
        }

        void writeAscii(String s) throws IOException {
            write(s.getBytes(StandardCharsets.US_ASCII));
        }

        void flush() throws IOException {
            mOut.flush();
        }
    }

    private static void writePdfBinary(
            OutputStream rawOut, List<PageInfo> pages, List<Bitmap> bitmaps) throws IOException {
        CountingOut out = new CountingOut(rawOut);
        // %PDF-1.4 header + 4 binary high-bit comment bytes per PDF 1.4 section 3.4.1
        out.write(new byte[] {
                0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34, 0x0A,
                0x25, (byte) 0xE2, (byte) 0xE3, (byte) 0xCF, (byte) 0xD3, 0x0A
        });

        int pageCount = pages.size();
        // Object 1: Catalog
        // Object 2: Pages root
        // Each page i uses 4 objects starting at 3 + 4 * i:
        //   obj (base):     Page dictionary
        //   obj (base + 1): Content stream
        //   obj (base + 2): RGB Image XObject
        //   obj (base + 3): Alpha SMask Image XObject
        int totalObjects = 3 + 4 * pageCount;
        long[] offsets = new long[totalObjects];

        // Object 1: Catalog
        offsets[1] = out.mOffset;
        out.writeAscii("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");

        // Object 2: Pages
        offsets[2] = out.mOffset;
        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < pageCount; i++) {
            if (i > 0) {
                kids.append(' ');
            }
            kids.append(3 + 4 * i).append(" 0 R");
        }
        out.writeAscii("2 0 obj\n<< /Type /Pages /Count " + pageCount
                + " /Kids [" + kids.toString() + "] >>\nendobj\n");

        for (int i = 0; i < pageCount; i++) {
            PageInfo info = pages.get(i);
            Bitmap bmp = bitmaps.get(i);
            int w = info.getPageWidth();
            int h = info.getPageHeight();
            int base = 3 + 4 * i;
            int contentObj = base + 1;
            int rgbObj = base + 2;
            int smaskObj = base + 3;

            int[] pixels = new int[w * h];
            bmp.getPixels(pixels, 0, w, 0, 0, w, h);

            byte[] rgbRaw = new byte[w * h * 3];
            byte[] alphaRaw = new byte[w * h];
            for (int p = 0; p < pixels.length; p++) {
                int argb = pixels[p];
                alphaRaw[p] = (byte) ((argb >>> 24) & 0xFF);
                int rgbIdx = p * 3;
                rgbRaw[rgbIdx] = (byte) ((argb >>> 16) & 0xFF);
                rgbRaw[rgbIdx + 1] = (byte) ((argb >>> 8) & 0xFF);
                rgbRaw[rgbIdx + 2] = (byte) (argb & 0xFF);
            }

            byte[] rgbCompressed = deflateBytes(rgbRaw);
            byte[] alphaCompressed = deflateBytes(alphaRaw);

            // Page dictionary
            offsets[base] = out.mOffset;
            Rect cr = info.getContentRect();
            out.writeAscii(base + " 0 obj\n"
                    + "<< /Type /Page /Parent 2 0 R"
                    + " /MediaBox [0 0 " + w + " " + h + "]"
                    + " /ShirazContentRect [" + cr.left + " " + cr.top
                    + " " + cr.right + " " + cr.bottom + "]"
                    + " /Resources << /XObject << /Im0 " + rgbObj + " 0 R >> >>"
                    + " /Contents " + contentObj + " 0 R >>\n"
                    + "endobj\n");

            // Content stream
            String contentOps = "q " + w + " 0 0 " + h + " 0 0 cm /Im0 Do Q";
            byte[] contentBytes = contentOps.getBytes(StandardCharsets.US_ASCII);
            offsets[contentObj] = out.mOffset;
            out.writeAscii(contentObj + " 0 obj\n<< /Length " + contentBytes.length + " >>\nstream\n");
            out.write(contentBytes);
            out.writeAscii("\nendstream\nendobj\n");

            // RGB Image XObject
            offsets[rgbObj] = out.mOffset;
            out.writeAscii(rgbObj + " 0 obj\n"
                    + "<< /Type /XObject /Subtype /Image"
                    + " /Width " + w + " /Height " + h
                    + " /ColorSpace /DeviceRGB /BitsPerComponent 8"
                    + " /SMask " + smaskObj + " 0 R"
                    + " /Filter /FlateDecode /Length " + rgbCompressed.length + " >>\n"
                    + "stream\n");
            out.write(rgbCompressed);
            out.writeAscii("\nendstream\nendobj\n");

            // Alpha SMask XObject
            offsets[smaskObj] = out.mOffset;
            out.writeAscii(smaskObj + " 0 obj\n"
                    + "<< /Type /XObject /Subtype /Image"
                    + " /Width " + w + " /Height " + h
                    + " /ColorSpace /DeviceGray /BitsPerComponent 8"
                    + " /Filter /FlateDecode /Length " + alphaCompressed.length + " >>\n"
                    + "stream\n");
            out.write(alphaCompressed);
            out.writeAscii("\nendstream\nendobj\n");
        }

        long xrefOffset = out.mOffset;
        out.writeAscii("xref\n0 " + totalObjects + "\n");
        out.writeAscii("0000000000 65535 f \n");
        for (int objId = 1; objId < totalObjects; objId++) {
            out.writeAscii(String.format(Locale.US, "%010d 00000 n \n", Long.valueOf(offsets[objId])));
        }
        out.writeAscii("trailer\n<< /Size " + totalObjects + " /Root 1 0 R >>\n"
                + "startxref\n" + xrefOffset + "\n%%EOF\n");
        out.flush();
    }

    public static final class Page {
        private Canvas mCanvas;
        private final PageInfo mPageInfo;
        final Bitmap mBitmap;

        private Page(Canvas canvas, PageInfo pageInfo, Bitmap bitmap) {
            this.mCanvas = canvas;
            this.mPageInfo = pageInfo;
            this.mBitmap = bitmap;
        }

        public Canvas getCanvas() {
            return mCanvas;
        }

        public PageInfo getInfo() {
            return mPageInfo;
        }

        boolean isFinished() {
            return mCanvas == null;
        }

        private void finish() {
            if (mCanvas != null) {
                mCanvas.setBitmap(null);
                mCanvas = null;
            }
        }
    }

    public static final class PageInfo {
        private int mPageWidth;
        private int mPageHeight;
        private Rect mContentRect;
        private int mPageNumber;

        private PageInfo() {
        }

        public int getPageWidth() {
            return mPageWidth;
        }

        public int getPageHeight() {
            return mPageHeight;
        }

        public Rect getContentRect() {
            return new Rect(mContentRect);
        }

        public int getPageNumber() {
            return mPageNumber;
        }

        public static final class Builder {
            private final PageInfo mPageInfo = new PageInfo();

            public Builder(int pageWidth, int pageHeight, int pageNumber) {
                if (pageWidth <= 0) {
                    throw new IllegalArgumentException("page width must be positive");
                }
                if (pageHeight <= 0) {
                    throw new IllegalArgumentException("page height must be positive");
                }
                if (pageNumber < 0) {
                    throw new IllegalArgumentException("pageNumber must be non negative");
                }
                mPageInfo.mPageWidth = pageWidth;
                mPageInfo.mPageHeight = pageHeight;
                mPageInfo.mPageNumber = pageNumber;
            }

            public Builder setContentRect(Rect contentRect) {
                if (contentRect != null) {
                    if (contentRect.left < 0
                            || contentRect.top < 0
                            || contentRect.right > mPageInfo.mPageWidth
                            || contentRect.bottom > mPageInfo.mPageHeight) {
                        throw new IllegalArgumentException("contentRect does not fit the page");
                    }
                    mPageInfo.mContentRect = new Rect(contentRect);
                } else {
                    mPageInfo.mContentRect = null;
                }
                return this;
            }

            public PageInfo create() {
                if (mPageInfo.mContentRect == null) {
                    mPageInfo.mContentRect = new Rect(
                            0, 0, mPageInfo.mPageWidth, mPageInfo.mPageHeight);
                }
                PageInfo result = new PageInfo();
                result.mPageWidth = mPageInfo.mPageWidth;
                result.mPageHeight = mPageInfo.mPageHeight;
                result.mPageNumber = mPageInfo.mPageNumber;
                result.mContentRect = new Rect(mPageInfo.mContentRect);
                return result;
            }
        }
    }
}
