// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.pdf;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.pdf.content.PdfPageGotoLinkContent;
import android.graphics.pdf.content.PdfPageImageContent;
import android.graphics.pdf.content.PdfPageLinkContent;
import android.graphics.pdf.content.PdfPageTextContent;
import android.graphics.pdf.models.FormEditRecord;
import android.graphics.pdf.models.FormWidgetInfo;
import android.graphics.pdf.models.PageMatchBounds;
import android.graphics.pdf.models.selection.PageSelection;
import android.graphics.pdf.models.selection.SelectionBoundary;
import android.os.ParcelFileDescriptor;
import dalvik.system.CloseGuard;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class PdfRendererPreV implements AutoCloseable {
    public static final int DOCUMENT_LINEARIZED_TYPE_NON_LINEARIZED = 0;
    public static final int DOCUMENT_LINEARIZED_TYPE_LINEARIZED = 1;

    public static final int PDF_FORM_TYPE_NONE = 0;
    public static final int PDF_FORM_TYPE_ACRO_FORM = 1;
    public static final int PDF_FORM_TYPE_XFA_FULL = 2;
    public static final int PDF_FORM_TYPE_XFA_FOREGROUND = 3;

    private final CloseGuard mCloseGuard = CloseGuard.get();
    private ParcelFileDescriptor mInput;
    private PdfRenderer.ParsedDocument mDocument;
    private final List<Page> mOpenPages = new ArrayList<Page>();

    public PdfRendererPreV(ParcelFileDescriptor fileDescriptor) throws IOException {
        Objects.requireNonNull(fileDescriptor, "input cannot be null");
        this.mInput = fileDescriptor;
        this.mDocument = PdfRenderer.parseDocument(fileDescriptor, null);
        this.mCloseGuard.open("close");
    }

    public PdfRendererPreV(ParcelFileDescriptor fileDescriptor, LoadParams loadParams)
            throws IOException {
        Objects.requireNonNull(fileDescriptor, "input cannot be null");
        Objects.requireNonNull(loadParams, "loadParams cannot be null");
        this.mInput = fileDescriptor;
        this.mDocument = PdfRenderer.parseDocument(fileDescriptor, loadParams);
        this.mCloseGuard.open("close");
    }

    public int getPageCount() {
        throwIfClosed();
        return mDocument.pages.size();
    }

    public int getDocumentLinearizationType() {
        throwIfClosed();
        return mDocument.linearizationType;
    }

    public Page openPage(int index) {
        throwIfClosed();
        if (index < 0 || index >= mDocument.pages.size()) {
            throw new IllegalArgumentException("Invalid page index: " + index);
        }
        Page page = new Page(index, mDocument.pages.get(index));
        mOpenPages.add(page);
        return page;
    }

    public int getPdfFormType() {
        throwIfClosed();
        return mDocument.formType;
    }

    public void write(ParcelFileDescriptor destination, boolean removePasswordProtection)
            throws IOException {
        throwIfClosed();
        Objects.requireNonNull(destination, "destination cannot be null");
        mDocument.writeTo(destination, removePasswordProtection);
    }

    @Override
    public void close() {
        throwIfClosed();
        doClose();
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            if (mCloseGuard != null) {
                mCloseGuard.warnIfOpen();
            }
            doClose();
        } finally {
            super.finalize();
        }
    }

    private void throwIfClosed() {
        if (mInput == null) {
            throw new IllegalStateException("Already closed");
        }
    }

    private void doClose() {
        if (!mOpenPages.isEmpty()) {
            ArrayList<Page> copy = new ArrayList<Page>(mOpenPages);
            for (int i = 0; i < copy.size(); i++) {
                Page p = copy.get(i);
                if (!p.mClosed) {
                    p.close();
                }
            }
            mOpenPages.clear();
        }
        if (mDocument != null) {
            mDocument.dispose();
            mDocument = null;
        }
        if (mInput != null) {
            try {
                mInput.close();
            } catch (IOException ignored) {
            }
            mInput = null;
            mCloseGuard.close();
        }
    }

    public final class Page implements AutoCloseable {
        private final CloseGuard mPageCloseGuard = CloseGuard.get();
        private final int mIndex;
        private PdfRenderer.ParsedPage mParsedPage;
        boolean mClosed;

        private Page(int index, PdfRenderer.ParsedPage parsedPage) {
            this.mIndex = index;
            this.mParsedPage = parsedPage;
            this.mPageCloseGuard.open("close");
        }

        public int getIndex() {
            return mIndex;
        }

        public void render(
                Bitmap destination, Rect destClip, Matrix transform, RenderParams params) {
            throwIfPageClosed();
            Objects.requireNonNull(params, "RenderParams cannot be null");
            int mode = params.getRenderMode();
            if (mode != RenderParams.RENDER_MODE_FOR_DISPLAY
                    && mode != RenderParams.RENDER_MODE_FOR_PRINT) {
                throw new IllegalArgumentException("Invalid render mode: " + mode);
            }
            mParsedPage.renderOnto(destination, destClip, transform, mode, params.getRenderFlags());
        }

        public List<PdfPageTextContent> getTextContents() {
            throwIfPageClosed();
            return mParsedPage.getTextContents();
        }

        public List<PdfPageImageContent> getImageContents() {
            throwIfPageClosed();
            return mParsedPage.getImageContents();
        }

        public int getWidth() {
            throwIfPageClosed();
            return mParsedPage.width;
        }

        public int getHeight() {
            throwIfPageClosed();
            return mParsedPage.height;
        }

        public List<PageMatchBounds> searchText(String query) {
            throwIfPageClosed();
            Objects.requireNonNull(query, "query cannot be null");
            return mParsedPage.searchText(query);
        }

        public PageSelection selectContent(SelectionBoundary start, SelectionBoundary stop) {
            throwIfPageClosed();
            Objects.requireNonNull(start, "start cannot be null");
            Objects.requireNonNull(stop, "stop cannot be null");
            return mParsedPage.selectContent(mIndex, start, stop);
        }

        public List<PdfPageLinkContent> getLinkContents() {
            throwIfPageClosed();
            return mParsedPage.getLinkContents();
        }

        public List<PdfPageGotoLinkContent> getGotoLinks() {
            throwIfPageClosed();
            return mParsedPage.getGotoLinks();
        }

        public List<FormWidgetInfo> getFormWidgetInfos() {
            throwIfPageClosed();
            return mParsedPage.getFormWidgetInfos(null);
        }

        public List<FormWidgetInfo> getFormWidgetInfos(int[] types) {
            throwIfPageClosed();
            Objects.requireNonNull(types, "types cannot be null");
            return mParsedPage.getFormWidgetInfos(types);
        }

        public FormWidgetInfo getFormWidgetInfoAtIndex(int widgetIndex) {
            throwIfPageClosed();
            return mParsedPage.getFormWidgetInfoAtIndex(widgetIndex);
        }

        public FormWidgetInfo getFormWidgetInfoAtPosition(int x, int y) {
            throwIfPageClosed();
            return mParsedPage.getFormWidgetInfoAtPosition(x, y);
        }

        public List<Rect> applyEdit(FormEditRecord editRecord) {
            throwIfPageClosed();
            Objects.requireNonNull(editRecord, "editRecord cannot be null");
            return mParsedPage.applyEdit(mIndex, editRecord);
        }

        @Override
        public void close() {
            throwIfPageClosed();
            doPageClose();
        }

        @Override
        protected void finalize() throws Throwable {
            try {
                if (mPageCloseGuard != null) {
                    mPageCloseGuard.warnIfOpen();
                }
                if (!mClosed) {
                    doPageClose();
                }
            } finally {
                super.finalize();
            }
        }

        private void throwIfPageClosed() {
            if (mClosed) {
                throw new IllegalStateException("Page already closed");
            }
        }

        private void doPageClose() {
            mClosed = true;
            mParsedPage = null;
            mPageCloseGuard.close();
            mOpenPages.remove(this);
        }
    }
}
