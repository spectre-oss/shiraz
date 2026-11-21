// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;

import android.graphics.fonts.Font;
import android.graphics.text.MeasuredText;

/** Display-list recording Canvas obtained from RenderNode.beginRecording(). */
public final class RecordingCanvas extends Canvas {
    private final RenderNode owner;
    private Bitmap backing;
    private final int recordedWidth;
    private final int recordedHeight;
    private boolean finished;

    RecordingCanvas(RenderNode owner, Bitmap backing, int width, int height) {
        super();
        super.setBitmap(backing);
        this.owner = owner;
        this.backing = backing;
        this.recordedWidth = width;
        this.recordedHeight = height;
    }

    Bitmap shirazFinish() {
        finished = true;
        Bitmap result = backing;
        backing = null;
        return result;
    }

    @Override
    public void setDensity(int density) {
        super.setDensity(density);
    }

    @Override
    public boolean isHardwareAccelerated() {
        return true;
    }

    @Override
    public void setBitmap(Bitmap bitmap) {
        throw new UnsupportedOperationException("Cannot setBitmap on a RecordingCanvas");
    }

    @Override
    public boolean isOpaque() {
        return false;
    }

    @Override
    public int getWidth() {
        return recordedWidth;
    }

    @Override
    public int getHeight() {
        return recordedHeight;
    }

    @Override
    public int getMaximumBitmapWidth() {
        return 8192;
    }

    @Override
    public int getMaximumBitmapHeight() {
        return 8192;
    }

    @Override
    public void enableZ() {
        super.enableZ();
    }

    @Override
    public void disableZ() {
        super.disableZ();
    }

    @Override
    public void drawRenderNode(RenderNode renderNode) {
        if (renderNode == owner) {
            throw new IllegalArgumentException("Cannot draw a RenderNode into itself");
        }
        super.drawRenderNode(renderNode);
    }

    @Override
    public final void drawTextOnPath(
            char[] text, int index, int count, Path path, float hOffset, float vOffset, Paint paint) {
        super.drawTextOnPath(text, index, count, path, hOffset, vOffset, paint);
    }

    @Override
    public final void drawTextOnPath(
            String text, Path path, float hOffset, float vOffset, Paint paint) {
        super.drawTextOnPath(text, path, hOffset, vOffset, paint);
    }

    @Override
    public final void drawPosText(
            char[] text, int index, int count, float[] pos, Paint paint) {
        super.drawPosText(text, index, count, pos, paint);
    }

    @Override
    public final void drawPosText(String text, float[] pos, Paint paint) {
        super.drawPosText(text, pos, paint);
    }

    @Override
    public final void drawOval(float left, float top, float right, float bottom, Paint paint) {
        super.drawOval(left, top, right, bottom, paint);
    }

    @Override
    public final void drawOval(RectF oval, Paint paint) {
        super.drawOval(oval, paint);
    }

    @Override
    public final void drawDoubleRoundRect(
            RectF outer, float outerRx, float outerRy,
            RectF inner, float innerRx, float innerRy, Paint paint) {
        super.drawDoubleRoundRect(outer, outerRx, outerRy, inner, innerRx, innerRy, paint);
    }

    @Override
    public final void drawDoubleRoundRect(
            RectF outer, float[] outerRadii, RectF inner, float[] innerRadii, Paint paint) {
        super.drawDoubleRoundRect(outer, outerRadii, inner, innerRadii, paint);
    }

    @Override
    public final void drawPoint(float x, float y, Paint paint) {
        super.drawPoint(x, y, paint);
    }

    @Override
    public final void drawPatch(NinePatch patch, Rect dst, Paint paint) {
        super.drawPatch(patch, dst, paint);
    }

    @Override
    public final void drawPatch(NinePatch patch, RectF dst, Paint paint) {
        super.drawPatch(patch, dst, paint);
    }

    @Override
    public final void drawBitmap(Bitmap bitmap, float left, float top, Paint paint) {
        super.drawBitmap(bitmap, left, top, paint);
    }

    @Override
    public final void drawBitmap(Bitmap bitmap, Matrix matrix, Paint paint) {
        super.drawBitmap(bitmap, matrix, paint);
    }

    @Override
    public final void drawBitmap(Bitmap bitmap, Rect src, Rect dst, Paint paint) {
        super.drawBitmap(bitmap, src, dst, paint);
    }

    @Override
    public final void drawBitmap(Bitmap bitmap, Rect src, RectF dst, Paint paint) {
        super.drawBitmap(bitmap, src, dst, paint);
    }

    @Override
    public final void drawBitmap(
            int[] colors, int offset, int stride, float x, float y,
            int width, int height, boolean hasAlpha, Paint paint) {
        super.drawBitmap(colors, offset, stride, x, y, width, height, hasAlpha, paint);
    }

    @Override
    public final void drawBitmap(
            int[] colors, int offset, int stride, int x, int y,
            int width, int height, boolean hasAlpha, Paint paint) {
        super.drawBitmap(colors, offset, stride, x, y, width, height, hasAlpha, paint);
    }

    @Override
    public final void drawColor(int color) {
        super.drawColor(color);
    }

    @Override
    public final void drawColor(int color, PorterDuff.Mode mode) {
        super.drawColor(color, mode);
    }

    @Override
    public final void drawColor(int color, BlendMode mode) {
        super.drawColor(color, mode);
    }

    @Override
    public final void drawColor(long color, BlendMode mode) {
        super.drawColor(color, mode);
    }

    @Override
    public final void drawPoints(float[] pts, int offset, int count, Paint paint) {
        super.drawPoints(pts, offset, count, paint);
    }

    @Override
    public final void drawPoints(float[] pts, Paint paint) {
        super.drawPoints(pts, paint);
    }

    @Override
    public final void drawRoundRect(
            float left, float top, float right, float bottom, float rx, float ry, Paint paint) {
        super.drawRoundRect(left, top, right, bottom, rx, ry, paint);
    }

    @Override
    public final void drawRoundRect(RectF rect, float rx, float ry, Paint paint) {
        super.drawRoundRect(rect, rx, ry, paint);
    }

    @Override
    public final void drawPaint(Paint paint) {
        super.drawPaint(paint);
    }

    @Override
    public final void drawBitmapMesh(
            Bitmap bitmap, int meshWidth, int meshHeight,
            float[] verts, int vertOffset, int[] colors, int colorOffset, Paint paint) {
        super.drawBitmapMesh(bitmap, meshWidth, meshHeight, verts, vertOffset, colors, colorOffset, paint);
    }

    @Override
    public final void drawTextRun(
            char[] text, int index, int count, int contextIndex, int contextCount,
            float x, float y, boolean isRtl, Paint paint) {
        super.drawTextRun(text, index, count, contextIndex, contextCount, x, y, isRtl, paint);
    }

    @Override
    public final void drawTextRun(
            CharSequence text, int start, int end, int contextStart, int contextEnd,
            float x, float y, boolean isRtl, Paint paint) {
        super.drawTextRun(text, start, end, contextStart, contextEnd, x, y, isRtl, paint);
    }

    @Override
    public void drawTextRun(
            MeasuredText text, int start, int end, int contextStart, int contextEnd,
            float x, float y, boolean isRtl, Paint paint) {
        super.drawTextRun(text, start, end, contextStart, contextEnd, x, y, isRtl, paint);
    }

    @Override
    public final void drawMesh(Mesh mesh, BlendMode blendMode, Paint paint) {
        super.drawMesh(mesh, blendMode, paint);
    }

    @Override
    public final void drawCircle(float cx, float cy, float radius, Paint paint) {
        super.drawCircle(cx, cy, radius, paint);
    }

    @Override
    public final void drawText(char[] text, int index, int count, float x, float y, Paint paint) {
        super.drawText(text, index, count, x, y, paint);
    }

    @Override
    public final void drawText(
            CharSequence text, int start, int end, float x, float y, Paint paint) {
        super.drawText(text, start, end, x, y, paint);
    }

    @Override
    public final void drawText(String text, float x, float y, Paint paint) {
        super.drawText(text, x, y, paint);
    }

    @Override
    public final void drawText(String text, int start, int end, float x, float y, Paint paint) {
        super.drawText(text, start, end, x, y, paint);
    }

    @Override
    public final void drawArc(
            float left, float top, float right, float bottom,
            float startAngle, float sweepAngle, boolean useCenter, Paint paint) {
        super.drawArc(left, top, right, bottom, startAngle, sweepAngle, useCenter, paint);
    }

    @Override
    public final void drawArc(
            RectF oval, float startAngle, float sweepAngle, boolean useCenter, Paint paint) {
        super.drawArc(oval, startAngle, sweepAngle, useCenter, paint);
    }

    @Override
    public final void drawLine(float startX, float startY, float stopX, float stopY, Paint paint) {
        super.drawLine(startX, startY, stopX, stopY, paint);
    }

    @Override
    public final void drawVertices(
            VertexMode mode, int vertexCount, float[] verts, int vertOffset,
            float[] texs, int texOffset, int[] colors, int colorOffset,
            short[] indices, int indexOffset, int indexCount, Paint paint) {
        super.drawVertices(
                mode, vertexCount, verts, vertOffset, texs, texOffset,
                colors, colorOffset, indices, indexOffset, indexCount, paint);
    }

    @Override
    public final void drawRGB(int r, int g, int b) {
        super.drawRGB(r, g, b);
    }

    @Override
    public final void drawLines(float[] pts, int offset, int count, Paint paint) {
        super.drawLines(pts, offset, count, paint);
    }

    @Override
    public final void drawLines(float[] pts, Paint paint) {
        super.drawLines(pts, paint);
    }

    @Override
    public void drawRegion(Region region, Paint paint) {
        super.drawRegion(region, paint);
    }

    @Override
    public final void drawPicture(Picture picture) {
        super.drawPicture(picture);
    }

    @Override
    public final void drawPicture(Picture picture, Rect dst) {
        super.drawPicture(picture, dst);
    }

    @Override
    public final void drawPicture(Picture picture, RectF dst) {
        super.drawPicture(picture, dst);
    }

    @Override
    public void drawGlyphs(
            int[] glyphIds, int glyphIdOffset, float[] positions, int positionOffset,
            int glyphCount, Font font, Paint paint) {
        super.drawGlyphs(glyphIds, glyphIdOffset, positions, positionOffset, glyphCount, font, paint);
    }

    @Override
    public final void drawARGB(int a, int r, int g, int b) {
        super.drawARGB(a, r, g, b);
    }

    @Override
    public final void drawPath(Path path, Paint paint) {
        super.drawPath(path, paint);
    }

    @Override
    public final void drawRect(float left, float top, float right, float bottom, Paint paint) {
        super.drawRect(left, top, right, bottom, paint);
    }

    @Override
    public final void drawRect(Rect r, Paint paint) {
        super.drawRect(r, paint);
    }

    @Override
    public final void drawRect(RectF rect, Paint paint) {
        super.drawRect(rect, paint);
    }
}
