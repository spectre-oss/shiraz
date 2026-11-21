// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.pdf;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.pdf.content.PdfPageGotoLinkContent;
import android.graphics.pdf.content.PdfPageImageContent;
import android.graphics.pdf.content.PdfPageLinkContent;
import android.graphics.pdf.content.PdfPageTextContent;
import android.graphics.pdf.models.FormEditRecord;
import android.graphics.pdf.models.FormWidgetInfo;
import android.graphics.pdf.models.ListItem;
import android.graphics.pdf.models.PageMatchBounds;
import android.graphics.pdf.models.selection.PageSelection;
import android.graphics.pdf.models.selection.SelectionBoundary;
import android.net.Uri;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import dalvik.system.CloseGuard;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public final class PdfRenderer implements AutoCloseable {
    public static final int DOCUMENT_LINEARIZED_TYPE_NON_LINEARIZED = 0;
    public static final int DOCUMENT_LINEARIZED_TYPE_LINEARIZED = 1;

    public static final int PDF_FORM_TYPE_NONE = 0;
    public static final int PDF_FORM_TYPE_ACRO_FORM = 1;
    public static final int PDF_FORM_TYPE_XFA_FULL = 2;
    public static final int PDF_FORM_TYPE_XFA_FOREGROUND = 3;

    private final CloseGuard mCloseGuard = CloseGuard.get();
    private final boolean mEnforceSingleOpenPage;
    private ParcelFileDescriptor mInput;
    private ParsedDocument mDocument;
    private final List<Page> mOpenPages = new ArrayList<Page>();

    public PdfRenderer(ParcelFileDescriptor fileDescriptor) throws IOException {
        Objects.requireNonNull(fileDescriptor, "input cannot be null");
        this.mEnforceSingleOpenPage = true;
        this.mInput = fileDescriptor;
        this.mDocument = parseDocument(fileDescriptor, null);
        this.mCloseGuard.open("close");
    }

    public PdfRenderer(ParcelFileDescriptor fileDescriptor, LoadParams loadParams)
            throws IOException {
        Objects.requireNonNull(fileDescriptor, "input cannot be null");
        Objects.requireNonNull(loadParams, "loadParams cannot be null");
        this.mEnforceSingleOpenPage = false;
        this.mInput = fileDescriptor;
        this.mDocument = parseDocument(fileDescriptor, loadParams);
        this.mCloseGuard.open("close");
    }

    @Override
    public void close() {
        throwIfClosed();
        if (mEnforceSingleOpenPage && !mOpenPages.isEmpty()) {
            throw new IllegalStateException("Current page not closed");
        }
        doClose();
    }

    public int getPageCount() {
        throwIfClosed();
        return mDocument.pages.size();
    }

    public boolean shouldScaleForPrinting() {
        throwIfClosed();
        return mDocument.scaleForPrinting;
    }

    public int getDocumentLinearizationType() {
        throwIfClosed();
        return mDocument.linearizationType;
    }

    public Page openPage(int index) {
        throwIfClosed();
        if (mEnforceSingleOpenPage && !mOpenPages.isEmpty()) {
            throw new IllegalStateException("Current page not closed");
        }
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
        public static final int RENDER_MODE_FOR_DISPLAY = 1;
        public static final int RENDER_MODE_FOR_PRINT = 2;

        private final CloseGuard mPageCloseGuard = CloseGuard.get();
        private final int mIndex;
        private ParsedPage mParsedPage;
        boolean mClosed;

        private Page(int index, ParsedPage parsedPage) {
            this.mIndex = index;
            this.mParsedPage = parsedPage;
            this.mPageCloseGuard.open("close");
        }

        public int getIndex() {
            return mIndex;
        }

        public int getWidth() {
            throwIfPageClosed();
            return mParsedPage.width;
        }

        public int getHeight() {
            throwIfPageClosed();
            return mParsedPage.height;
        }

        public void render(
                Bitmap destination, Rect destClip, Matrix transform, int renderMode) {
            throwIfPageClosed();
            if (renderMode != RENDER_MODE_FOR_DISPLAY && renderMode != RENDER_MODE_FOR_PRINT) {
                throw new IllegalArgumentException("Invalid render mode: " + renderMode);
            }
            mParsedPage.renderOnto(destination, destClip, transform, renderMode, 0);
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

    // ---- Shared package-private PDF parsing & rendering engine ----

    static final class PdfObj {
        final int id;
        final String dictOrBody;
        final int streamStart;
        final int streamEnd;

        PdfObj(int id, String dictOrBody, int streamStart, int streamEnd) {
            this.id = id;
            this.dictOrBody = dictOrBody;
            this.streamStart = streamStart;
            this.streamEnd = streamEnd;
        }
    }

    static final class ParsedDocument {
        final byte[] rawBytes;
        final boolean encrypted;
        final int linearizationType;
        final int formType;
        final boolean scaleForPrinting;
        final List<ParsedPage> pages;

        ParsedDocument(
                byte[] rawBytes,
                boolean encrypted,
                int linearizationType,
                int formType,
                boolean scaleForPrinting,
                List<ParsedPage> pages) {
            this.rawBytes = rawBytes;
            this.encrypted = encrypted;
            this.linearizationType = linearizationType;
            this.formType = formType;
            this.scaleForPrinting = scaleForPrinting;
            this.pages = pages;
        }

        void writeTo(ParcelFileDescriptor destination, boolean removePasswordProtection)
                throws IOException {
            byte[] outBytes = rawBytes;
            if (removePasswordProtection && encrypted) {
                String iso = new String(rawBytes, StandardCharsets.ISO_8859_1);
                String cleaned = iso.replaceAll("/Encrypt\\s+\\d+\\s+\\d+\\s+R", "")
                        .replaceAll("/Encrypt\\s*<<[^>]*>>", "");
                outBytes = cleaned.getBytes(StandardCharsets.ISO_8859_1);
            }
            ParcelFileDescriptor dup = destination.dup();
            FileOutputStream fos = new ParcelFileDescriptor.AutoCloseOutputStream(dup);
            try {
                try {
                    fos.getChannel().position(0);
                    fos.getChannel().truncate(0);
                } catch (IOException ignored) {
                }
                fos.write(outBytes);
                fos.flush();
            } finally {
                fos.close();
            }
        }

        void dispose() {
            for (int i = 0; i < pages.size(); i++) {
                pages.get(i).dispose();
            }
        }
    }

    static final class ParsedPage {
        final byte[] docBytes;
        final Map<Integer, PdfObj> objects;
        final PdfObj pageObj;
        final String inheritedResources;
        final int width;
        final int height;
        final int rotation;

        private Bitmap mCachedRaster;
        private boolean mRasterDecoded;
        private List<PdfPageTextContent> mTextContents;
        private List<PdfPageImageContent> mImageContents;
        private List<PdfPageLinkContent> mLinkContents;
        private List<PdfPageGotoLinkContent> mGotoLinks;
        private final List<FormWidgetInfo> mWidgets = new ArrayList<FormWidgetInfo>();
        private boolean mMetadataParsed;

        ParsedPage(
                byte[] docBytes,
                Map<Integer, PdfObj> objects,
                PdfObj pageObj,
                String inheritedResources,
                int width,
                int height,
                int rotation) {
            this.docBytes = docBytes;
            this.objects = objects;
            this.pageObj = pageObj;
            this.inheritedResources = inheritedResources;
            this.width = width;
            this.height = height;
            this.rotation = rotation;
        }

        void dispose() {
            if (mCachedRaster != null && !mCachedRaster.isRecycled()) {
                mCachedRaster.recycle();
            }
            mCachedRaster = null;
        }

        void renderOnto(
                Bitmap destination,
                Rect destClip,
                Matrix transform,
                int renderMode,
                int renderFlags) {
            Objects.requireNonNull(destination, "destination cannot be null");
            if (destination.isRecycled()) {
                throw new IllegalArgumentException("destination bitmap is recycled");
            }
            if (!destination.isMutable()) {
                throw new IllegalArgumentException("destination bitmap must be mutable");
            }
            if (destination.getConfig() != Bitmap.Config.ARGB_8888) {
                throw new IllegalArgumentException("Unsupported pixel format: "
                        + destination.getConfig());
            }
            int contentLeft = 0;
            int contentTop = 0;
            int contentRight = destination.getWidth();
            int contentBottom = destination.getHeight();
            if (destClip != null) {
                if (destClip.left < 0
                        || destClip.top < 0
                        || destClip.right > destination.getWidth()
                        || destClip.bottom > destination.getHeight()) {
                    throw new IllegalArgumentException("destClip not in destination");
                }
                contentLeft = destClip.left;
                contentTop = destClip.top;
                contentRight = destClip.right;
                contentBottom = destClip.bottom;
            }
            if (contentLeft >= contentRight || contentTop >= contentBottom) {
                return;
            }
            Matrix effectiveTransform;
            if (transform == null) {
                effectiveTransform = new Matrix();
                effectiveTransform.postScale(
                        (float) (contentRight - contentLeft) / (float) width,
                        (float) (contentBottom - contentTop) / (float) height);
                effectiveTransform.postTranslate(contentLeft, contentTop);
            } else {
                if (!transform.isAffine()) {
                    throw new IllegalArgumentException("transform not affine");
                }
                effectiveTransform = transform;
            }

            Bitmap pageBitmap = ensurePageBitmap();
            Canvas canvas = new Canvas(destination);
            int saveCount = canvas.save();
            try {
                canvas.clipRect(contentLeft, contentTop, contentRight, contentBottom);
                Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
                // Fast exact 1:1 copy when identity transform and full bitmap match
                if (effectiveTransform.isIdentity()
                        && pageBitmap.getWidth() == destination.getWidth()
                        && pageBitmap.getHeight() == destination.getHeight()) {
                    canvas.drawBitmap(pageBitmap, 0f, 0f, null);
                } else {
                    canvas.drawBitmap(pageBitmap, effectiveTransform, paint);
                }
            } finally {
                canvas.restoreToCount(saveCount);
            }
        }

        private Bitmap ensurePageBitmap() {
            if (mRasterDecoded && mCachedRaster != null && !mCachedRaster.isRecycled()) {
                return mCachedRaster;
            }
            Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas pageCanvas = new Canvas(bmp);
            if (rotation == 90 || rotation == 180 || rotation == 270) {
                if (rotation == 90) {
                    pageCanvas.translate(width, 0);
                    pageCanvas.rotate(90);
                } else if (rotation == 180) {
                    pageCanvas.translate(width, height);
                    pageCanvas.rotate(180);
                } else {
                    pageCanvas.translate(0, height);
                    pageCanvas.rotate(270);
                }
            }

            Map<String, Integer> xObjectRefs = parseXObjectMap(
                    resolveResourcesDict(pageObj.dictOrBody, inheritedResources, objects), objects);
            byte[] contentData = collectContentStreams(pageObj.dictOrBody, docBytes, objects);
            if (contentData.length > 0) {
                executeContentStream(pageCanvas, contentData, xObjectRefs);
            } else if (!xObjectRefs.isEmpty()) {
                // Fallback if XObject exists without explicit Do stream
                for (Map.Entry<String, Integer> entry : xObjectRefs.entrySet()) {
                    Bitmap img = decodeImageXObject(entry.getValue().intValue());
                    if (img != null) {
                        pageCanvas.drawBitmap(
                                img, null, new RectF(0, 0, width, height), null);
                        img.recycle();
                    }
                }
            }
            mCachedRaster = bmp;
            mRasterDecoded = true;
            return bmp;
        }

        private Bitmap decodeImageXObject(int objId) {
            PdfObj obj = objects.get(Integer.valueOf(objId));
            if (obj == null || obj.streamStart < 0) {
                return null;
            }
            String dict = obj.dictOrBody;
            if (!dict.contains("/Image")) {
                return null;
            }
            int imgW = parseIntEntry(dict, "/Width", -1, objects);
            int imgH = parseIntEntry(dict, "/Height", -1, objects);
            if (imgW <= 0 || imgH <= 0 || imgW > 8192 || imgH > 8192) {
                return null;
            }
            byte[] decoded = extractStreamBytes(obj, docBytes);
            if (decoded == null) {
                return null;
            }

            byte[] alphaBytes = null;
            int smaskRef = parseRefEntry(dict, "/SMask");
            if (smaskRef > 0) {
                PdfObj smaskObj = objects.get(Integer.valueOf(smaskRef));
                if (smaskObj != null && smaskObj.streamStart >= 0) {
                    alphaBytes = extractStreamBytes(smaskObj, docBytes);
                }
            }

            int pixelCount = imgW * imgH;
            int[] pixels = new int[pixelCount];
            boolean isGray = dict.contains("/DeviceGray");
            if (isGray) {
                int limit = Math.min(pixelCount, decoded.length);
                for (int i = 0; i < limit; i++) {
                    int g = decoded[i] & 0xFF;
                    int a = (alphaBytes != null && i < alphaBytes.length)
                            ? (alphaBytes[i] & 0xFF) : 0xFF;
                    pixels[i] = (a << 24) | (g << 16) | (g << 8) | g;
                }
            } else {
                int limit = Math.min(pixelCount, decoded.length / 3);
                for (int i = 0; i < limit; i++) {
                    int base = i * 3;
                    int r = decoded[base] & 0xFF;
                    int g = decoded[base + 1] & 0xFF;
                    int b = decoded[base + 2] & 0xFF;
                    int a = (alphaBytes != null && i < alphaBytes.length)
                            ? (alphaBytes[i] & 0xFF) : 0xFF;
                    pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
                }
            }

            Bitmap bmp = Bitmap.createBitmap(imgW, imgH, Bitmap.Config.ARGB_8888);
            bmp.setPixels(pixels, 0, imgW, 0, 0, imgW, imgH);
            return bmp;
        }

        private void executeContentStream(
                Canvas canvas, byte[] contentBytes, Map<String, Integer> xObjectRefs) {
            List<String> tokens = tokenizeContentStream(
                    new String(contentBytes, StandardCharsets.ISO_8859_1));
            ArrayList<String> operands = new ArrayList<String>();
            float[] ctm = new float[] { 1f, 0f, 0f, 1f, 0f, 0f };
            ArrayList<float[]> stateStack = new ArrayList<float[]>();
            int fillColor = Color.BLACK;
            int strokeColor = Color.BLACK;
            float lineWidth = 1f;
            Path currentPath = new Path();
            float fontSize = 12f;
            float textX = 0f;
            float textY = 0f;

            int pageH = (rotation == 90 || rotation == 270) ? width : height;

            for (int i = 0; i < tokens.size(); i++) {
                String tok = tokens.get(i);
                if ("q".equals(tok)) {
                    stateStack.add(new float[] {
                            ctm[0], ctm[1], ctm[2], ctm[3], ctm[4], ctm[5],
                            Float.intBitsToFloat(fillColor),
                            Float.intBitsToFloat(strokeColor),
                            lineWidth
                    });
                    operands.clear();
                } else if ("Q".equals(tok)) {
                    if (!stateStack.isEmpty()) {
                        float[] s = stateStack.remove(stateStack.size() - 1);
                        ctm[0] = s[0];
                        ctm[1] = s[1];
                        ctm[2] = s[2];
                        ctm[3] = s[3];
                        ctm[4] = s[4];
                        ctm[5] = s[5];
                        fillColor = Float.floatToRawIntBits(s[6]);
                        strokeColor = Float.floatToRawIntBits(s[7]);
                        lineWidth = s[8];
                    }
                    operands.clear();
                } else if ("cm".equals(tok)) {
                    if (operands.size() >= 6) {
                        int n = operands.size();
                        float a = parseFloatSafe(operands.get(n - 6), 1f);
                        float b = parseFloatSafe(operands.get(n - 5), 0f);
                        float c = parseFloatSafe(operands.get(n - 4), 0f);
                        float d = parseFloatSafe(operands.get(n - 3), 1f);
                        float e = parseFloatSafe(operands.get(n - 2), 0f);
                        float f = parseFloatSafe(operands.get(n - 1), 0f);
                        float na = a * ctm[0] + b * ctm[2];
                        float nb = a * ctm[1] + b * ctm[3];
                        float nc = c * ctm[0] + d * ctm[2];
                        float nd = c * ctm[1] + d * ctm[3];
                        float ne = e * ctm[0] + f * ctm[2] + ctm[4];
                        float nf = e * ctm[1] + f * ctm[3] + ctm[5];
                        ctm[0] = na;
                        ctm[1] = nb;
                        ctm[2] = nc;
                        ctm[3] = nd;
                        ctm[4] = ne;
                        ctm[5] = nf;
                    }
                    operands.clear();
                } else if ("Do".equals(tok)) {
                    if (!operands.isEmpty()) {
                        String name = operands.get(operands.size() - 1);
                        Integer ref = xObjectRefs.get(name);
                        if (ref != null) {
                            Bitmap img = decodeImageXObject(ref.intValue());
                            if (img != null) {
                                // Map image [0..imgW, 0..imgH] (top-down) through PDF unit square:
                                // (u, 1-v) maps to PDF (a*u + c*(1-v) + e, b*u + d*(1-v) + f)
                                // and then to top-down (x_pdf, pageH - y_pdf)
                                float invW = 1f / img.getWidth();
                                float invH = 1f / img.getHeight();
                                Matrix m = new Matrix();
                                m.setValues(new float[] {
                                        ctm[0] * invW,
                                        -ctm[2] * invH,
                                        ctm[2] + ctm[4],
                                        -ctm[1] * invW,
                                        ctm[3] * invH,
                                        pageH - (ctm[3] + ctm[5]),
                                        0f, 0f, 1f
                                });
                                if (m.isIdentity()
                                        && img.getWidth() == canvas.getWidth()
                                        && img.getHeight() == canvas.getHeight()) {
                                    canvas.drawBitmap(img, 0f, 0f, null);
                                } else {
                                    Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
                                    canvas.drawBitmap(img, m, p);
                                }
                                img.recycle();
                            }
                        }
                    }
                    operands.clear();
                } else if ("rg".equals(tok) || "RG".equals(tok)) {
                    if (operands.size() >= 3) {
                        int n = operands.size();
                        int r = clamp255(parseFloatSafe(operands.get(n - 3), 0f) * 255f);
                        int g = clamp255(parseFloatSafe(operands.get(n - 2), 0f) * 255f);
                        int b = clamp255(parseFloatSafe(operands.get(n - 1), 0f) * 255f);
                        int col = Color.rgb(r, g, b);
                        if ("rg".equals(tok)) {
                            fillColor = col;
                        } else {
                            strokeColor = col;
                        }
                    }
                    operands.clear();
                } else if ("g".equals(tok) || "G".equals(tok)) {
                    if (!operands.isEmpty()) {
                        int v = clamp255(
                                parseFloatSafe(operands.get(operands.size() - 1), 0f) * 255f);
                        int col = Color.rgb(v, v, v);
                        if ("g".equals(tok)) {
                            fillColor = col;
                        } else {
                            strokeColor = col;
                        }
                    }
                    operands.clear();
                } else if ("w".equals(tok)) {
                    if (!operands.isEmpty()) {
                        lineWidth = Math.max(
                                0.5f, parseFloatSafe(operands.get(operands.size() - 1), 1f));
                    }
                    operands.clear();
                } else if ("re".equals(tok)) {
                    if (operands.size() >= 4) {
                        int n = operands.size();
                        float rx = parseFloatSafe(operands.get(n - 4), 0f);
                        float ry = parseFloatSafe(operands.get(n - 3), 0f);
                        float rw = parseFloatSafe(operands.get(n - 2), 0f);
                        float rh = parseFloatSafe(operands.get(n - 1), 0f);
                        float x0 = ctm[0] * rx + ctm[2] * ry + ctm[4];
                        float y0 = pageH - (ctm[1] * rx + ctm[3] * ry + ctm[5]);
                        float x1 = ctm[0] * (rx + rw) + ctm[2] * (ry + rh) + ctm[4];
                        float y1 = pageH - (ctm[1] * (rx + rw) + ctm[3] * (ry + rh) + ctm[5]);
                        currentPath.addRect(
                                Math.min(x0, x1),
                                Math.min(y0, y1),
                                Math.max(x0, x1),
                                Math.max(y0, y1),
                                Path.Direction.CW);
                    }
                    operands.clear();
                } else if ("m".equals(tok) || "l".equals(tok)) {
                    if (operands.size() >= 2) {
                        int n = operands.size();
                        float px = parseFloatSafe(operands.get(n - 2), 0f);
                        float py = parseFloatSafe(operands.get(n - 1), 0f);
                        float tx = ctm[0] * px + ctm[2] * py + ctm[4];
                        float ty = pageH - (ctm[1] * px + ctm[3] * py + ctm[5]);
                        if ("m".equals(tok)) {
                            currentPath.moveTo(tx, ty);
                        } else {
                            currentPath.lineTo(tx, ty);
                        }
                    }
                    operands.clear();
                } else if ("h".equals(tok)) {
                    currentPath.close();
                    operands.clear();
                } else if ("f".equals(tok) || "F".equals(tok) || "f*".equals(tok)) {
                    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(fillColor);
                    canvas.drawPath(currentPath, p);
                    currentPath.reset();
                    operands.clear();
                } else if ("S".equals(tok) || "s".equals(tok)) {
                    if ("s".equals(tok)) {
                        currentPath.close();
                    }
                    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(lineWidth);
                    p.setColor(strokeColor);
                    canvas.drawPath(currentPath, p);
                    currentPath.reset();
                    operands.clear();
                } else if ("B".equals(tok) || "b".equals(tok)) {
                    if ("b".equals(tok)) {
                        currentPath.close();
                    }
                    Paint fp = new Paint(Paint.ANTI_ALIAS_FLAG);
                    fp.setStyle(Paint.Style.FILL);
                    fp.setColor(fillColor);
                    canvas.drawPath(currentPath, fp);
                    Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
                    sp.setStyle(Paint.Style.STROKE);
                    sp.setStrokeWidth(lineWidth);
                    sp.setColor(strokeColor);
                    canvas.drawPath(currentPath, sp);
                    currentPath.reset();
                    operands.clear();
                } else if ("n".equals(tok)) {
                    currentPath.reset();
                    operands.clear();
                } else if ("BT".equals(tok)) {
                    textX = 0f;
                    textY = 0f;
                    operands.clear();
                } else if ("ET".equals(tok)) {
                    operands.clear();
                } else if ("Tf".equals(tok)) {
                    if (!operands.isEmpty()) {
                        fontSize = Math.max(
                                1f, parseFloatSafe(operands.get(operands.size() - 1), 12f));
                    }
                    operands.clear();
                } else if ("Td".equals(tok) || "TD".equals(tok)) {
                    if (operands.size() >= 2) {
                        int n = operands.size();
                        textX += parseFloatSafe(operands.get(n - 2), 0f);
                        textY += parseFloatSafe(operands.get(n - 1), 0f);
                    }
                    operands.clear();
                } else if ("Tm".equals(tok)) {
                    if (operands.size() >= 6) {
                        int n = operands.size();
                        textX = parseFloatSafe(operands.get(n - 2), 0f);
                        textY = parseFloatSafe(operands.get(n - 1), 0f);
                    }
                    operands.clear();
                } else if ("Tj".equals(tok) || "TJ".equals(tok)) {
                    StringBuilder sb = new StringBuilder();
                    for (int k = 0; k < operands.size(); k++) {
                        String op = operands.get(k);
                        if (op.startsWith("(") && op.endsWith(")")) {
                            sb.append(unescapePdfString(op.substring(1, op.length() - 1)));
                        }
                    }
                    if (sb.length() > 0) {
                        Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
                        tp.setColor(fillColor);
                        tp.setTextSize(fontSize);
                        float dx = ctm[0] * textX + ctm[2] * textY + ctm[4];
                        float dy = pageH - (ctm[1] * textX + ctm[3] * textY + ctm[5]);
                        canvas.drawText(sb.toString(), dx, dy, tp);
                    }
                    operands.clear();
                } else {
                    operands.add(tok);
                }
            }
        }

        private void ensureMetadataParsed() {
            if (mMetadataParsed) {
                return;
            }
            mMetadataParsed = true;
            mTextContents = new ArrayList<PdfPageTextContent>();
            mImageContents = new ArrayList<PdfPageImageContent>();
            mLinkContents = new ArrayList<PdfPageLinkContent>();
            mGotoLinks = new ArrayList<PdfPageGotoLinkContent>();

            byte[] contentData = collectContentStreams(pageObj.dictOrBody, docBytes, objects);
            if (contentData.length > 0) {
                extractTextRuns(contentData, mTextContents);
            }

            Map<String, Integer> xObjectRefs = parseXObjectMap(
                    resolveResourcesDict(pageObj.dictOrBody, inheritedResources, objects), objects);
            for (Map.Entry<String, Integer> entry : xObjectRefs.entrySet()) {
                PdfObj xo = objects.get(entry.getValue());
                if (xo != null && xo.dictOrBody.contains("/Image")) {
                    String alt = parsePdfLiteralString(xo.dictOrBody, "/Alt");
                    mImageContents.add(new PdfPageImageContent(alt == null ? "" : alt));
                }
            }

            // Parse /Annots if present
            List<Integer> annotIds = parseRefListEntry(pageObj.dictOrBody, "/Annots", objects);
            int widgetIdx = 0;
            for (int i = 0; i < annotIds.size(); i++) {
                PdfObj annot = objects.get(annotIds.get(i));
                if (annot == null) {
                    continue;
                }
                String dict = annot.dictOrBody;
                float[] rectVals = parseBox(dict, "/Rect", objects);
                RectF rf = rectVals == null
                        ? new RectF(0, 0, 0, 0)
                        : new RectF(
                                Math.min(rectVals[0], rectVals[2]),
                                Math.min(height - rectVals[3], height - rectVals[1]),
                                Math.max(rectVals[0], rectVals[2]),
                                Math.max(height - rectVals[3], height - rectVals[1]));
                if (dict.contains("/Link")) {
                    String uriStr = parsePdfLiteralString(dict, "/URI");
                    if (uriStr == null) {
                        int actionRef = parseRefEntry(dict, "/A");
                        if (actionRef > 0 && objects.containsKey(Integer.valueOf(actionRef))) {
                            String aDict = objects.get(Integer.valueOf(actionRef)).dictOrBody;
                            uriStr = parsePdfLiteralString(aDict, "/URI");
                        }
                    }
                    if (uriStr != null) {
                        mLinkContents.add(new PdfPageLinkContent(
                                Collections.singletonList(rf), Uri.parse(uriStr)));
                    } else if (dict.contains("/GoTo") || dict.contains("/Dest")) {
                        mGotoLinks.add(new PdfPageGotoLinkContent(
                                Collections.singletonList(rf),
                                new PdfPageGotoLinkContent.Destination(0, rf.left, rf.top, 1f)));
                    }
                } else if (dict.contains("/Widget")) {
                    int wType = FormWidgetInfo.WIDGET_TYPE_TEXTFIELD;
                    if (dict.contains("/Btn")) {
                        wType = FormWidgetInfo.WIDGET_TYPE_CHECKBOX;
                    } else if (dict.contains("/Ch")) {
                        wType = FormWidgetInfo.WIDGET_TYPE_LISTBOX;
                    } else if (dict.contains("/Sig")) {
                        wType = FormWidgetInfo.WIDGET_TYPE_SIGNATURE;
                    }
                    String val = parsePdfLiteralString(dict, "/V");
                    String label = parsePdfLiteralString(dict, "/TU");
                    if (label == null) {
                        label = parsePdfLiteralString(dict, "/T");
                    }
                    Rect intRect = new Rect(
                            Math.round(rf.left),
                            Math.round(rf.top),
                            Math.round(rf.right),
                            Math.round(rf.bottom));
                    FormWidgetInfo.Builder wb = new FormWidgetInfo.Builder(
                            wType,
                            widgetIdx++,
                            intRect,
                            val == null ? "" : val,
                            label == null ? "" : label);
                    if (wType == FormWidgetInfo.WIDGET_TYPE_TEXTFIELD) {
                        wb.setEditableText(true);
                    }
                    mWidgets.add(wb.build());
                }
            }
        }

        private void extractTextRuns(byte[] contentBytes, List<PdfPageTextContent> out) {
            List<String> tokens = tokenizeContentStream(
                    new String(contentBytes, StandardCharsets.ISO_8859_1));
            ArrayList<String> operands = new ArrayList<String>();
            boolean inText = false;
            float fontSize = 12f;
            float tx = 0f;
            float ty = 0f;
            StringBuilder blockText = new StringBuilder();
            ArrayList<RectF> blockBounds = new ArrayList<RectF>();

            for (int i = 0; i < tokens.size(); i++) {
                String tok = tokens.get(i);
                if ("BT".equals(tok)) {
                    inText = true;
                    tx = 0f;
                    ty = 0f;
                    blockText.setLength(0);
                    blockBounds.clear();
                    operands.clear();
                } else if ("ET".equals(tok)) {
                    if (inText && blockText.length() > 0) {
                        out.add(new PdfPageTextContent(
                                blockText.toString(), new ArrayList<RectF>(blockBounds)));
                    }
                    inText = false;
                    operands.clear();
                } else if ("Tf".equals(tok)) {
                    if (!operands.isEmpty()) {
                        fontSize = Math.max(
                                1f, parseFloatSafe(operands.get(operands.size() - 1), 12f));
                    }
                    operands.clear();
                } else if ("Td".equals(tok) || "TD".equals(tok)) {
                    if (operands.size() >= 2) {
                        int n = operands.size();
                        tx += parseFloatSafe(operands.get(n - 2), 0f);
                        ty += parseFloatSafe(operands.get(n - 1), 0f);
                    }
                    operands.clear();
                } else if ("Tm".equals(tok)) {
                    if (operands.size() >= 6) {
                        int n = operands.size();
                        tx = parseFloatSafe(operands.get(n - 2), 0f);
                        ty = parseFloatSafe(operands.get(n - 1), 0f);
                    }
                    operands.clear();
                } else if ("Tj".equals(tok) || "TJ".equals(tok)) {
                    StringBuilder run = new StringBuilder();
                    for (int k = 0; k < operands.size(); k++) {
                        String op = operands.get(k);
                        if (op.startsWith("(") && op.endsWith(")")) {
                            run.append(unescapePdfString(op.substring(1, op.length() - 1)));
                        }
                    }
                    if (run.length() > 0) {
                        if (blockText.length() > 0) {
                            blockText.append(' ');
                        }
                        blockText.append(run);
                        float topY = Math.max(0f, height - ty - fontSize);
                        float botY = Math.min(height, height - ty);
                        float adv = run.length() * fontSize * 0.55f;
                        blockBounds.add(new RectF(tx, topY, tx + adv, Math.max(topY + 1f, botY)));
                    }
                    operands.clear();
                } else {
                    operands.add(tok);
                }
            }
        }

        List<PdfPageTextContent> getTextContents() {
            ensureMetadataParsed();
            return Collections.unmodifiableList(
                    new ArrayList<PdfPageTextContent>(mTextContents));
        }

        List<PdfPageImageContent> getImageContents() {
            ensureMetadataParsed();
            return Collections.unmodifiableList(
                    new ArrayList<PdfPageImageContent>(mImageContents));
        }

        List<PdfPageLinkContent> getLinkContents() {
            ensureMetadataParsed();
            return Collections.unmodifiableList(
                    new ArrayList<PdfPageLinkContent>(mLinkContents));
        }

        List<PdfPageGotoLinkContent> getGotoLinks() {
            ensureMetadataParsed();
            return Collections.unmodifiableList(
                    new ArrayList<PdfPageGotoLinkContent>(mGotoLinks));
        }

        List<PageMatchBounds> searchText(String query) {
            ensureMetadataParsed();
            if (query.isEmpty() || mTextContents.isEmpty()) {
                return Collections.emptyList();
            }
            StringBuilder full = new StringBuilder();
            for (int i = 0; i < mTextContents.size(); i++) {
                if (i > 0) {
                    full.append('\n');
                }
                full.append(mTextContents.get(i).getText());
            }
            String haystack = full.toString().toLowerCase(Locale.ROOT);
            String needle = query.toLowerCase(Locale.ROOT);
            ArrayList<PageMatchBounds> matches = new ArrayList<PageMatchBounds>();
            int from = 0;
            while (true) {
                int idx = haystack.indexOf(needle, from);
                if (idx < 0) {
                    break;
                }
                RectF box = new RectF(0f, 0f, Math.min(width, needle.length() * 7f), 12f);
                if (!mTextContents.get(0).getBounds().isEmpty()) {
                    box = new RectF(mTextContents.get(0).getBounds().get(0));
                }
                matches.add(new PageMatchBounds(Collections.singletonList(box), idx));
                from = idx + Math.max(1, needle.length());
            }
            return Collections.unmodifiableList(matches);
        }

        PageSelection selectContent(
                int pageIndex, SelectionBoundary start, SelectionBoundary stop) {
            ensureMetadataParsed();
            if (mTextContents.isEmpty()) {
                return null;
            }
            StringBuilder stream = new StringBuilder();
            for (int i = 0; i < mTextContents.size(); i++) {
                if (i > 0) {
                    stream.append('\n');
                }
                stream.append(mTextContents.get(i).getText());
            }
            int len = stream.length();
            if (len == 0) {
                return null;
            }
            int sIdx = start.getIndex() >= 0 ? Math.min(len, start.getIndex()) : 0;
            int eIdx = stop.getIndex() >= 0 ? Math.min(len, stop.getIndex()) : len;
            boolean rtl = sIdx > eIdx;
            int lo = Math.min(sIdx, eIdx);
            int hi = Math.max(sIdx, eIdx);
            String selected = stream.substring(lo, hi);
            RectF bounds = mTextContents.get(0).getBounds().isEmpty()
                    ? new RectF(0f, 0f, width, 12f)
                    : mTextContents.get(0).getBounds().get(0);
            PdfPageTextContent selectedContent = new PdfPageTextContent(
                    selected, Collections.singletonList(bounds));
            SelectionBoundary resolvedStart = createSelectionBoundary(lo, null, rtl);
            SelectionBoundary resolvedStop = createSelectionBoundary(hi, null, rtl);
            return new PageSelection(
                    pageIndex,
                    resolvedStart,
                    resolvedStop,
                    Collections.singletonList(selectedContent));
        }

        private static SelectionBoundary createSelectionBoundary(
                int index, Point point, boolean isRtl) {
            Parcel p = Parcel.obtain();
            try {
                p.writeInt(index);
                if (point == null) {
                    p.writeInt(0);
                } else {
                    p.writeInt(1);
                    p.writeInt(point.x);
                    p.writeInt(point.y);
                }
                p.writeBoolean(isRtl);
                p.setDataPosition(0);
                return SelectionBoundary.CREATOR.createFromParcel(p);
            } finally {
                p.recycle();
            }
        }

        List<FormWidgetInfo> getFormWidgetInfos(int[] types) {
            ensureMetadataParsed();
            if (types == null || types.length == 0) {
                return Collections.unmodifiableList(new ArrayList<FormWidgetInfo>(mWidgets));
            }
            ArrayList<FormWidgetInfo> filtered = new ArrayList<FormWidgetInfo>();
            for (int i = 0; i < mWidgets.size(); i++) {
                FormWidgetInfo w = mWidgets.get(i);
                for (int t = 0; t < types.length; t++) {
                    if (w.getWidgetType() == types[t]) {
                        filtered.add(w);
                        break;
                    }
                }
            }
            return Collections.unmodifiableList(filtered);
        }

        FormWidgetInfo getFormWidgetInfoAtIndex(int widgetIndex) {
            ensureMetadataParsed();
            for (int i = 0; i < mWidgets.size(); i++) {
                FormWidgetInfo w = mWidgets.get(i);
                if (w.getWidgetIndex() == widgetIndex) {
                    return w;
                }
            }
            throw new IllegalArgumentException("No widget at index " + widgetIndex);
        }

        FormWidgetInfo getFormWidgetInfoAtPosition(int x, int y) {
            ensureMetadataParsed();
            for (int i = 0; i < mWidgets.size(); i++) {
                FormWidgetInfo w = mWidgets.get(i);
                if (w.getWidgetRect().contains(x, y)) {
                    return w;
                }
            }
            throw new IllegalArgumentException(
                    "No widget at position (" + x + ", " + y + ")");
        }

        List<Rect> applyEdit(int pageIndex, FormEditRecord record) {
            ensureMetadataParsed();
            if (record.getPageNumber() != pageIndex) {
                throw new IllegalArgumentException("Edit record page mismatch");
            }
            int idx = record.getWidgetIndex();
            for (int i = 0; i < mWidgets.size(); i++) {
                FormWidgetInfo old = mWidgets.get(i);
                if (old.getWidgetIndex() == idx) {
                    if (old.isReadOnly()) {
                        throw new IllegalArgumentException("Widget is read-only");
                    }
                    String nextText = old.getTextValue();
                    List<ListItem> nextItems = old.getListItems();
                    if (record.getType() == FormEditRecord.EDIT_TYPE_SET_TEXT) {
                        nextText = record.getText();
                    } else if (record.getType() == FormEditRecord.EDIT_TYPE_CLICK) {
                        nextText = "true".equals(nextText) ? "false" : "true";
                    } else if (record.getType() == FormEditRecord.EDIT_TYPE_SET_INDICES
                            && !nextItems.isEmpty()) {
                        int[] sel = record.getSelectedIndices();
                        ArrayList<ListItem> updated = new ArrayList<ListItem>(nextItems.size());
                        for (int k = 0; k < nextItems.size(); k++) {
                            boolean chosen = false;
                            for (int s = 0; s < sel.length; s++) {
                                if (sel[s] == k) {
                                    chosen = true;
                                    break;
                                }
                            }
                            updated.add(new ListItem(nextItems.get(k).getLabel(), chosen));
                        }
                        nextItems = updated;
                    }
                    FormWidgetInfo.Builder b = new FormWidgetInfo.Builder(
                            old.getWidgetType(),
                            old.getWidgetIndex(),
                            old.getWidgetRect(),
                            nextText == null ? "" : nextText,
                            old.getAccessibilityLabel() == null ? "" : old.getAccessibilityLabel());
                    b.setReadOnly(old.isReadOnly());
                    if (old.getWidgetType() == FormWidgetInfo.WIDGET_TYPE_TEXTFIELD
                            || old.getWidgetType() == FormWidgetInfo.WIDGET_TYPE_COMBOBOX) {
                        b.setEditableText(old.isEditableText());
                        if (old.getFontSize() >= 0f) {
                            b.setFontSize(old.getFontSize());
                        }
                    }
                    if (old.getWidgetType() == FormWidgetInfo.WIDGET_TYPE_TEXTFIELD) {
                        b.setMultiLineText(old.isMultiLineText());
                        if (old.getMaxLength() >= 0) {
                            b.setMaxLength(old.getMaxLength());
                        }
                    }
                    if (old.getWidgetType() == FormWidgetInfo.WIDGET_TYPE_LISTBOX) {
                        b.setMultiSelect(old.isMultiSelect());
                    }
                    if (old.getWidgetType() == FormWidgetInfo.WIDGET_TYPE_COMBOBOX
                            || old.getWidgetType() == FormWidgetInfo.WIDGET_TYPE_LISTBOX) {
                        b.setListItems(nextItems);
                    }
                    mWidgets.set(i, b.build());
                    return Collections.singletonList(old.getWidgetRect());
                }
            }
            throw new IllegalArgumentException("Cannot apply edit to unknown widget " + idx);
        }
    }

    static ParsedDocument parseDocument(
            ParcelFileDescriptor fileDescriptor, LoadParams loadParams) throws IOException {
        byte[] rawBytes = readAllBytes(fileDescriptor);
        if (rawBytes.length < 8) {
            throw new IOException("PDF file is too short");
        }
        int headerLimit = Math.min(rawBytes.length, 1024);
        String headerCheck = new String(rawBytes, 0, headerLimit, StandardCharsets.ISO_8859_1);
        if (!headerCheck.contains("%PDF-")) {
            throw new IOException("Missing %PDF- header");
        }

        Map<Integer, PdfObj> objects = scanObjects(rawBytes);
        String tail = new String(
                rawBytes,
                Math.max(0, rawBytes.length - 4096),
                Math.min(rawBytes.length, 4096),
                StandardCharsets.ISO_8859_1);

        boolean encrypted = hasUnquotedToken(tail, "/Encrypt");
        String encryptDict = null;
        if (!encrypted) {
            for (PdfObj obj : objects.values()) {
                if (hasUnquotedToken(obj.dictOrBody, "/Encrypt")) {
                    encrypted = true;
                    break;
                }
            }
        }
        if (encrypted) {
            int encRef = parseRefEntry(tail, "/Encrypt");
            if (encRef > 0 && objects.containsKey(Integer.valueOf(encRef))) {
                encryptDict = objects.get(Integer.valueOf(encRef)).dictOrBody;
            } else {
                encryptDict = tail;
            }
            String supplied = loadParams == null ? null : loadParams.getPassword();
            String expected = encryptDict == null
                    ? null : parsePdfLiteralString(encryptDict, "/UserPassword");
            if (expected == null && encryptDict != null) {
                expected = parsePdfLiteralString(encryptDict, "/Password");
            }
            if (supplied == null || expected == null || !expected.equals(supplied)) {
                throw new SecurityException("Password required or incorrect password");
            }
        }

        int linearizationType = DOCUMENT_LINEARIZED_TYPE_NON_LINEARIZED;
        String firstChunk = new String(
                rawBytes, 0, Math.min(rawBytes.length, 2048), StandardCharsets.ISO_8859_1);
        if (hasUnquotedToken(firstChunk, "/Linearized")) {
            linearizationType = DOCUMENT_LINEARIZED_TYPE_LINEARIZED;
        }

        int formType = PDF_FORM_TYPE_NONE;
        boolean scaleForPrinting = true;
        PdfObj catalog = findCatalog(objects, tail);
        if (catalog != null) {
            String catDict = catalog.dictOrBody;
            if (catDict.contains("/PrintScaling") && catDict.contains("/None")) {
                scaleForPrinting = false;
            } else {
                int vpRef = parseRefEntry(catDict, "/ViewerPreferences");
                if (vpRef > 0 && objects.containsKey(Integer.valueOf(vpRef))) {
                    String vpDict = objects.get(Integer.valueOf(vpRef)).dictOrBody;
                    if (vpDict.contains("/PrintScaling") && vpDict.contains("/None")) {
                        scaleForPrinting = false;
                    }
                }
            }
            String acroDict = null;
            int acroRef = parseRefEntry(catDict, "/AcroForm");
            if (acroRef > 0 && objects.containsKey(Integer.valueOf(acroRef))) {
                acroDict = objects.get(Integer.valueOf(acroRef)).dictOrBody;
            } else if (catDict.contains("/AcroForm")) {
                acroDict = catDict;
            }
            if (acroDict != null) {
                if (acroDict.contains("/XFA")) {
                    formType = acroDict.contains("/Foreground")
                            ? PDF_FORM_TYPE_XFA_FOREGROUND : PDF_FORM_TYPE_XFA_FULL;
                } else if (acroDict.contains("/Fields")) {
                    formType = PDF_FORM_TYPE_ACRO_FORM;
                }
            }
        }

        List<ParsedPage> pages = new ArrayList<ParsedPage>();
        if (catalog != null) {
            int pagesRootRef = parseRefEntry(catalog.dictOrBody, "/Pages");
            if (pagesRootRef > 0) {
                Set<Integer> visited = new HashSet<Integer>();
                collectPagesRecursive(
                        pagesRootRef,
                        null,
                        null,
                        0,
                        rawBytes,
                        objects,
                        visited,
                        pages);
            }
        }
        if (pages.isEmpty()) {
            // Fallback: scan /Type /Page objects in object ID order
            for (PdfObj obj : objects.values()) {
                if (isPageObject(obj.dictOrBody)) {
                    float[] box = parseBox(obj.dictOrBody, "/MediaBox", objects);
                    if (box == null) {
                        box = new float[] { 0f, 0f, 612f, 792f };
                    }
                    int rot = ((parseIntEntry(obj.dictOrBody, "/Rotate", 0, objects) % 360) + 360)
                            % 360;
                    int pw = Math.max(1, Math.round(Math.abs(box[2] - box[0])));
                    int ph = Math.max(1, Math.round(Math.abs(box[3] - box[1])));
                    if (rot == 90 || rot == 270) {
                        int tmp = pw;
                        pw = ph;
                        ph = tmp;
                    }
                    pages.add(new ParsedPage(rawBytes, objects, obj, null, pw, ph, rot));
                }
            }
        }
        if (pages.isEmpty()) {
            throw new IOException("PDF document contains no valid pages");
        }

        return new ParsedDocument(
                rawBytes, encrypted, linearizationType, formType, scaleForPrinting, pages);
    }

    private static byte[] readAllBytes(ParcelFileDescriptor pfd) throws IOException {
        ParcelFileDescriptor dup = pfd.dup();
        FileInputStream fis = new ParcelFileDescriptor.AutoCloseInputStream(dup);
        try {
            try {
                fis.getChannel().position(0);
            } catch (IOException ignored) {
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = fis.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        } finally {
            fis.close();
        }
    }

    private static Map<Integer, PdfObj> scanObjects(byte[] raw) {
        TreeMap<Integer, PdfObj> map = new TreeMap<Integer, PdfObj>();
        int len = raw.length;
        int pos = 0;
        while (pos < len) {
            // Skip whitespace & comments
            byte b = raw[pos];
            if (b == '%') {
                while (pos < len && raw[pos] != '\n' && raw[pos] != '\r') {
                    pos++;
                }
                continue;
            }
            if (b < '0' || b > '9') {
                pos++;
                continue;
            }
            // Check "<objId> <gen> obj" at boundary
            if (pos > 0 && isTokenChar(raw[pos - 1])) {
                pos++;
                continue;
            }
            int numStart = pos;
            while (pos < len && raw[pos] >= '0' && raw[pos] <= '9') {
                pos++;
            }
            if (pos >= len || !isPdfWhitespace(raw[pos])) {
                continue;
            }
            int objId;
            try {
                objId = Integer.parseInt(
                        new String(raw, numStart, pos - numStart, StandardCharsets.US_ASCII));
            } catch (NumberFormatException e) {
                continue;
            }
            while (pos < len && isPdfWhitespace(raw[pos])) {
                pos++;
            }
            int genStart = pos;
            while (pos < len && raw[pos] >= '0' && raw[pos] <= '9') {
                pos++;
            }
            if (pos == genStart || pos >= len || !isPdfWhitespace(raw[pos])) {
                continue;
            }
            while (pos < len && isPdfWhitespace(raw[pos])) {
                pos++;
            }
            if (pos + 3 > len
                    || raw[pos] != 'o'
                    || raw[pos + 1] != 'b'
                    || raw[pos + 2] != 'j'
                    || (pos + 3 < len && isTokenChar(raw[pos + 3]))) {
                pos = genStart;
                continue;
            }
            pos += 3;
            int bodyStart = pos;
            int streamKeyword = findKeywordOutsideStrings(raw, bodyStart, len, "stream", "endobj");
            if (streamKeyword >= 0) {
                String dict = new String(
                        raw, bodyStart, streamKeyword - bodyStart, StandardCharsets.ISO_8859_1);
                int dataStart = streamKeyword + 6;
                if (dataStart < len && raw[dataStart] == '\r') {
                    dataStart++;
                }
                if (dataStart < len && raw[dataStart] == '\n') {
                    dataStart++;
                }
                int directLen = parseDirectInt(dict, "/Length");
                int dataEnd = -1;
                if (directLen >= 0 && dataStart + directLen <= len) {
                    int probe = dataStart + directLen;
                    int endStreamPos = indexOfBytes(
                            raw, probe, Math.min(len, probe + 32), "endstream");
                    if (endStreamPos >= 0) {
                        dataEnd = dataStart + directLen;
                        pos = endStreamPos + 9;
                    }
                }
                if (dataEnd < 0) {
                    int endStreamPos = indexOfBytes(raw, dataStart, len, "endstream");
                    if (endStreamPos < 0) {
                        break;
                    }
                    dataEnd = endStreamPos;
                    if (dataEnd > dataStart && raw[dataEnd - 1] == '\n') {
                        dataEnd--;
                    }
                    if (dataEnd > dataStart && raw[dataEnd - 1] == '\r') {
                        dataEnd--;
                    }
                    pos = endStreamPos + 9;
                }
                int endObj = indexOfBytes(raw, pos, Math.min(len, pos + 64), "endobj");
                if (endObj >= 0) {
                    pos = endObj + 6;
                }
                map.put(Integer.valueOf(objId), new PdfObj(objId, dict, dataStart, dataEnd));
            } else {
                int endObj = indexOfBytes(raw, bodyStart, len, "endobj");
                if (endObj < 0) {
                    break;
                }
                String body = new String(
                        raw, bodyStart, endObj - bodyStart, StandardCharsets.ISO_8859_1);
                map.put(Integer.valueOf(objId), new PdfObj(objId, body, -1, -1));
                pos = endObj + 6;
            }
        }
        return map;
    }

    private static PdfObj findCatalog(Map<Integer, PdfObj> objects, String tail) {
        int rootRef = parseRefEntry(tail, "/Root");
        if (rootRef > 0 && objects.containsKey(Integer.valueOf(rootRef))) {
            return objects.get(Integer.valueOf(rootRef));
        }
        for (PdfObj obj : objects.values()) {
            if (obj.dictOrBody.contains("/Catalog")) {
                return obj;
            }
        }
        return null;
    }

    private static boolean isPageObject(String dict) {
        int idx = 0;
        while (true) {
            int p = dict.indexOf("/Page", idx);
            if (p < 0) {
                return false;
            }
            int after = p + 5;
            if (after >= dict.length() || !Character.isLetter(dict.charAt(after))) {
                // Make sure this is /Type /Page and not /PageMode or /Pages
                return dict.contains("/Type");
            }
            idx = after;
        }
    }

    private static void collectPagesRecursive(
            int nodeId,
            float[] inheritedMediaBox,
            String inheritedResources,
            int inheritedRotate,
            byte[] rawBytes,
            Map<Integer, PdfObj> objects,
            Set<Integer> visited,
            List<ParsedPage> out) {
        if (!visited.add(Integer.valueOf(nodeId))) {
            return;
        }
        PdfObj node = objects.get(Integer.valueOf(nodeId));
        if (node == null) {
            return;
        }
        String dict = node.dictOrBody;
        float[] box = parseBox(dict, "/MediaBox", objects);
        if (box == null) {
            box = inheritedMediaBox;
        }
        String res = extractSubDictOrRef(dict, "/Resources", objects);
        if (res == null) {
            res = inheritedResources;
        }
        int rot = parseIntEntry(dict, "/Rotate", inheritedRotate, objects);

        if (dict.contains("/Kids")) {
            List<Integer> kids = parseRefListEntry(dict, "/Kids", objects);
            for (int i = 0; i < kids.size(); i++) {
                collectPagesRecursive(
                        kids.get(i).intValue(),
                        box,
                        res,
                        rot,
                        rawBytes,
                        objects,
                        visited,
                        out);
            }
        } else if (isPageObject(dict)) {
            if (box == null) {
                box = new float[] { 0f, 0f, 612f, 792f };
            }
            int normRot = ((rot % 360) + 360) % 360;
            int pw = Math.max(1, Math.round(Math.abs(box[2] - box[0])));
            int ph = Math.max(1, Math.round(Math.abs(box[3] - box[1])));
            if (normRot == 90 || normRot == 270) {
                int tmp = pw;
                pw = ph;
                ph = tmp;
            }
            out.add(new ParsedPage(rawBytes, objects, node, res, pw, ph, normRot));
        }
    }

    private static byte[] collectContentStreams(
            String pageDict, byte[] docBytes, Map<Integer, PdfObj> objects) {
        int cIdx = pageDict.indexOf("/Contents");
        if (cIdx < 0) {
            return new byte[0];
        }
        String after = pageDict.substring(cIdx + 9).trim();
        List<Integer> streamIds = new ArrayList<Integer>();
        if (after.startsWith("[")) {
            streamIds.addAll(parseRefListEntry(pageDict, "/Contents", objects));
        } else {
            int ref = parseRefEntry(pageDict, "/Contents");
            if (ref > 0) {
                PdfObj target = objects.get(Integer.valueOf(ref));
                if (target != null && target.streamStart < 0
                        && target.dictOrBody.trim().startsWith("[")) {
                    streamIds.addAll(parseRefListFromBracket(target.dictOrBody));
                } else if (target != null) {
                    streamIds.add(Integer.valueOf(ref));
                }
            }
        }
        if (streamIds.isEmpty()) {
            return new byte[0];
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        for (int i = 0; i < streamIds.size(); i++) {
            PdfObj sObj = objects.get(streamIds.get(i));
            if (sObj != null && sObj.streamStart >= 0) {
                byte[] part = extractStreamBytes(sObj, docBytes);
                if (part != null && part.length > 0) {
                    if (bos.size() > 0) {
                        bos.write('\n');
                    }
                    bos.write(part, 0, part.length);
                }
            }
        }
        return bos.toByteArray();
    }

    private static byte[] extractStreamBytes(PdfObj obj, byte[] docBytes) {
        if (obj.streamStart < 0 || obj.streamEnd < obj.streamStart) {
            return null;
        }
        int length = obj.streamEnd - obj.streamStart;
        byte[] slice = new byte[length];
        System.arraycopy(docBytes, obj.streamStart, slice, 0, length);
        if (obj.dictOrBody.contains("/FlateDecode")) {
            byte[] inflated = tryInflate(slice, false);
            if (inflated == null) {
                inflated = tryInflate(slice, true);
            }
            return inflated;
        }
        return slice;
    }

    private static byte[] tryInflate(byte[] input, boolean nowrap) {
        Inflater inflater = new Inflater(nowrap);
        try {
            inflater.setInput(input);
            ByteArrayOutputStream bos = new ByteArrayOutputStream(Math.max(128, input.length * 2));
            byte[] buf = new byte[8192];
            while (!inflater.finished()) {
                int count = inflater.inflate(buf);
                if (count > 0) {
                    bos.write(buf, 0, count);
                } else if (inflater.needsInput() || inflater.needsDictionary()) {
                    break;
                }
            }
            return bos.toByteArray();
        } catch (DataFormatException e) {
            return null;
        } finally {
            inflater.end();
        }
    }

    private static String resolveResourcesDict(
            String pageDict, String inheritedResources, Map<Integer, PdfObj> objects) {
        String own = extractSubDictOrRef(pageDict, "/Resources", objects);
        return own != null ? own : (inheritedResources != null ? inheritedResources : "");
    }

    private static String extractSubDictOrRef(
            String dict, String key, Map<Integer, PdfObj> objects) {
        int idx = dict.indexOf(key);
        if (idx < 0) {
            return null;
        }
        int pos = idx + key.length();
        while (pos < dict.length() && Character.isWhitespace(dict.charAt(pos))) {
            pos++;
        }
        if (pos + 1 < dict.length() && dict.charAt(pos) == '<' && dict.charAt(pos + 1) == '<') {
            int depth = 0;
            int start = pos;
            while (pos + 1 < dict.length()) {
                if (dict.charAt(pos) == '<' && dict.charAt(pos + 1) == '<') {
                    depth++;
                    pos += 2;
                } else if (dict.charAt(pos) == '>' && dict.charAt(pos + 1) == '>') {
                    depth--;
                    pos += 2;
                    if (depth == 0) {
                        return dict.substring(start, pos);
                    }
                } else {
                    pos++;
                }
            }
            return dict.substring(start);
        }
        int ref = parseRefEntry(dict, key);
        if (ref > 0 && objects.containsKey(Integer.valueOf(ref))) {
            return objects.get(Integer.valueOf(ref)).dictOrBody;
        }
        return null;
    }

    private static Map<String, Integer> parseXObjectMap(
            String resourcesDict, Map<Integer, PdfObj> objects) {
        if (resourcesDict == null || resourcesDict.isEmpty()) {
            return Collections.emptyMap();
        }
        String xoDict = extractSubDictOrRef(resourcesDict, "/XObject", objects);
        if (xoDict == null || xoDict.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Integer> result = new HashMap<String, Integer>();
        List<String> toks = tokenizeContentStream(xoDict);
        for (int i = 0; i + 3 < toks.size(); i++) {
            String t = toks.get(i);
            if (t.startsWith("/") && "R".equals(toks.get(i + 3))) {
                try {
                    int objNum = Integer.parseInt(toks.get(i + 1));
                    result.put(t, Integer.valueOf(objNum));
                    i += 3;
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return result;
    }

    private static float[] parseBox(String dict, String key, Map<Integer, PdfObj> objects) {
        int idx = dict.indexOf(key);
        if (idx < 0) {
            return null;
        }
        String sub = dict.substring(idx + key.length()).trim();
        if (!sub.startsWith("[")) {
            int ref = parseRefEntry(dict, key);
            if (ref > 0 && objects.containsKey(Integer.valueOf(ref))) {
                sub = objects.get(Integer.valueOf(ref)).dictOrBody.trim();
            }
        }
        int open = sub.indexOf('[');
        int close = sub.indexOf(']', open + 1);
        if (open < 0 || close < 0) {
            return null;
        }
        String[] parts = sub.substring(open + 1, close).trim().split("\\s+");
        if (parts.length < 4) {
            return null;
        }
        return new float[] {
                parseFloatSafe(parts[0], 0f),
                parseFloatSafe(parts[1], 0f),
                parseFloatSafe(parts[2], 612f),
                parseFloatSafe(parts[3], 792f)
        };
    }

    private static int parseDirectInt(String dict, String key) {
        int idx = dict.indexOf(key);
        if (idx < 0) {
            return -1;
        }
        int pos = idx + key.length();
        while (pos < dict.length() && Character.isWhitespace(dict.charAt(pos))) {
            pos++;
        }
        int start = pos;
        if (pos < dict.length() && (dict.charAt(pos) == '-' || dict.charAt(pos) == '+')) {
            pos++;
        }
        while (pos < dict.length() && Character.isDigit(dict.charAt(pos))) {
            pos++;
        }
        if (pos == start) {
            return -1;
        }
        // Check if it's an indirect ref "<num> <gen> R"
        int afterNum = pos;
        while (afterNum < dict.length() && Character.isWhitespace(dict.charAt(afterNum))) {
            afterNum++;
        }
        int genStart = afterNum;
        while (afterNum < dict.length() && Character.isDigit(dict.charAt(afterNum))) {
            afterNum++;
        }
        if (afterNum > genStart) {
            while (afterNum < dict.length() && Character.isWhitespace(dict.charAt(afterNum))) {
                afterNum++;
            }
            if (afterNum < dict.length() && dict.charAt(afterNum) == 'R') {
                return -1;
            }
        }
        try {
            return Integer.parseInt(dict.substring(start, pos));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static int parseIntEntry(
            String dict, String key, int defaultValue, Map<Integer, PdfObj> objects) {
        int direct = parseDirectInt(dict, key);
        if (direct != -1 || dict.indexOf(key) < 0) {
            return direct != -1 ? direct : defaultValue;
        }
        int ref = parseRefEntry(dict, key);
        if (ref > 0 && objects.containsKey(Integer.valueOf(ref))) {
            try {
                return Integer.parseInt(objects.get(Integer.valueOf(ref)).dictOrBody.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    private static int parseRefEntry(String dict, String key) {
        int idx = dict.indexOf(key);
        if (idx < 0) {
            return -1;
        }
        int pos = idx + key.length();
        while (pos < dict.length() && Character.isWhitespace(dict.charAt(pos))) {
            pos++;
        }
        int nStart = pos;
        while (pos < dict.length() && Character.isDigit(dict.charAt(pos))) {
            pos++;
        }
        if (pos == nStart) {
            return -1;
        }
        int objNum;
        try {
            objNum = Integer.parseInt(dict.substring(nStart, pos));
        } catch (NumberFormatException e) {
            return -1;
        }
        while (pos < dict.length() && Character.isWhitespace(dict.charAt(pos))) {
            pos++;
        }
        int gStart = pos;
        while (pos < dict.length() && Character.isDigit(dict.charAt(pos))) {
            pos++;
        }
        if (pos == gStart) {
            return -1;
        }
        while (pos < dict.length() && Character.isWhitespace(dict.charAt(pos))) {
            pos++;
        }
        if (pos < dict.length() && dict.charAt(pos) == 'R') {
            return objNum;
        }
        return -1;
    }

    private static List<Integer> parseRefListEntry(
            String dict, String key, Map<Integer, PdfObj> objects) {
        int idx = dict.indexOf(key);
        if (idx < 0) {
            return Collections.emptyList();
        }
        String sub = dict.substring(idx + key.length()).trim();
        if (!sub.startsWith("[")) {
            int ref = parseRefEntry(dict, key);
            if (ref > 0 && objects.containsKey(Integer.valueOf(ref))) {
                sub = objects.get(Integer.valueOf(ref)).dictOrBody.trim();
            }
        }
        return parseRefListFromBracket(sub);
    }

    private static List<Integer> parseRefListFromBracket(String sub) {
        int open = sub.indexOf('[');
        int close = sub.indexOf(']', open + 1);
        if (open < 0 || close < 0) {
            return Collections.emptyList();
        }
        String[] parts = sub.substring(open + 1, close).trim().split("\\s+");
        ArrayList<Integer> refs = new ArrayList<Integer>();
        for (int i = 0; i + 2 < parts.length; i++) {
            if ("R".equals(parts[i + 2])) {
                try {
                    refs.add(Integer.valueOf(Integer.parseInt(parts[i])));
                    i += 2;
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return refs;
    }

    private static String parsePdfLiteralString(String dict, String key) {
        int searchFrom = 0;
        while (true) {
            int idx = dict.indexOf(key, searchFrom);
            if (idx < 0) {
                return null;
            }
            int afterKey = idx + key.length();
            searchFrom = afterKey;
            if (afterKey < dict.length() && Character.isLetterOrDigit(dict.charAt(afterKey))) {
                continue;
            }
            int pos = afterKey;
            while (pos < dict.length() && Character.isWhitespace(dict.charAt(pos))) {
                pos++;
            }
            if (pos >= dict.length() || dict.charAt(pos) != '(') {
                continue;
            }
            pos++;
            int start = pos;
            int depth = 1;
            while (pos < dict.length() && depth > 0) {
                char c = dict.charAt(pos);
                if (c == '\\') {
                    pos += 2;
                    continue;
                }
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                    if (depth == 0) {
                        return unescapePdfString(dict.substring(start, pos));
                    }
                }
                pos++;
            }
            return null;
        }
    }

    private static String unescapePdfString(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\\' && i + 1 < raw.length()) {
                char next = raw.charAt(++i);
                switch (next) {
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'b':
                        sb.append('\b');
                        break;
                    case 'f':
                        sb.append('\f');
                        break;
                    case '(':
                    case ')':
                    case '\\':
                        sb.append(next);
                        break;
                    default:
                        if (next >= '0' && next <= '7') {
                            int oct = next - '0';
                            for (int k = 0; k < 2 && i + 1 < raw.length(); k++) {
                                char d = raw.charAt(i + 1);
                                if (d >= '0' && d <= '7') {
                                    oct = (oct << 3) | (d - '0');
                                    i++;
                                } else {
                                    break;
                                }
                            }
                            sb.append((char) oct);
                        } else {
                            sb.append(next);
                        }
                        break;
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static List<String> tokenizeContentStream(String content) {
        ArrayList<String> tokens = new ArrayList<String>();
        int len = content.length();
        int i = 0;
        while (i < len) {
            char c = content.charAt(i);
            if (Character.isWhitespace(c) || c == '\0') {
                i++;
                continue;
            }
            if (c == '%') {
                while (i < len && content.charAt(i) != '\n' && content.charAt(i) != '\r') {
                    i++;
                }
                continue;
            }
            if (c == '(') {
                int start = i++;
                int depth = 1;
                while (i < len && depth > 0) {
                    char ch = content.charAt(i);
                    if (ch == '\\') {
                        i += 2;
                        continue;
                    }
                    if (ch == '(') {
                        depth++;
                    } else if (ch == ')') {
                        depth--;
                    }
                    i++;
                }
                tokens.add(content.substring(start, Math.min(len, i)));
                continue;
            }
            if (c == '[' || c == ']') {
                i++;
                continue;
            }
            if (c == '<' && i + 1 < len && content.charAt(i + 1) == '<') {
                tokens.add("<<");
                i += 2;
                continue;
            }
            if (c == '>' && i + 1 < len && content.charAt(i + 1) == '>') {
                tokens.add(">>");
                i += 2;
                continue;
            }
            if (c == '/') {
                int start = i++;
                while (i < len && !Character.isWhitespace(content.charAt(i))
                        && "()<>[]{}/%".indexOf(content.charAt(i)) < 0) {
                    i++;
                }
                tokens.add(content.substring(start, i));
                continue;
            }
            int start = i++;
            while (i < len && !Character.isWhitespace(content.charAt(i))
                    && "()<>[]{}/%".indexOf(content.charAt(i)) < 0) {
                i++;
            }
            tokens.add(content.substring(start, i));
        }
        return tokens;
    }

    private static boolean hasUnquotedToken(String text, String token) {
        boolean inParens = false;
        for (int i = 0; i <= text.length() - token.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\') {
                i++;
                continue;
            }
            if (c == '(') {
                inParens = true;
            } else if (c == ')') {
                inParens = false;
            } else if (!inParens && text.regionMatches(i, token, 0, token.length())) {
                int after = i + token.length();
                if (after >= text.length() || !Character.isLetterOrDigit(text.charAt(after))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int findKeywordOutsideStrings(
            byte[] raw, int from, int to, String target, String stopAt) {
        byte[] tBytes = target.getBytes(StandardCharsets.US_ASCII);
        byte[] sBytes = stopAt.getBytes(StandardCharsets.US_ASCII);
        int parenDepth = 0;
        for (int i = from; i < to; i++) {
            byte b = raw[i];
            if (b == '\\' && parenDepth > 0) {
                i++;
                continue;
            }
            if (b == '(') {
                parenDepth++;
                continue;
            }
            if (b == ')' && parenDepth > 0) {
                parenDepth--;
                continue;
            }
            if (parenDepth == 0) {
                if (matchesWord(raw, i, to, sBytes)) {
                    return -1;
                }
                if (matchesWord(raw, i, to, tBytes)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static boolean matchesWord(byte[] raw, int pos, int limit, byte[] word) {
        if (pos + word.length > limit) {
            return false;
        }
        if (pos > 0 && isTokenChar(raw[pos - 1])) {
            return false;
        }
        for (int k = 0; k < word.length; k++) {
            if (raw[pos + k] != word[k]) {
                return false;
            }
        }
        int after = pos + word.length;
        return after >= limit || !isTokenChar(raw[after]);
    }

    private static int indexOfBytes(byte[] raw, int from, int to, String needle) {
        byte[] nb = needle.getBytes(StandardCharsets.US_ASCII);
        int max = to - nb.length;
        for (int i = from; i <= max; i++) {
            if (raw[i] == nb[0]) {
                boolean match = true;
                for (int k = 1; k < nb.length; k++) {
                    if (raw[i + k] != nb[k]) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static boolean isPdfWhitespace(byte b) {
        return b == 0 || b == 9 || b == 10 || b == 12 || b == 13 || b == 32;
    }

    private static boolean isTokenChar(byte b) {
        return (b >= 'a' && b <= 'z')
                || (b >= 'A' && b <= 'Z')
                || (b >= '0' && b <= '9')
                || b == '_' || b == '-';
    }

    private static float parseFloatSafe(String s, float fallback) {
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int clamp255(float v) {
        int r = Math.round(v);
        return r < 0 ? 0 : (r > 255 ? 255 : r);
    }
}
