// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;

import java.util.concurrent.atomic.AtomicLong;

/** Display-list node holding recorded drawing commands and 2D/3D compositing properties. */
public final class RenderNode {
    private static final AtomicLong NEXT_ID = new AtomicLong(1L);

    private final long uniqueId = NEXT_ID.getAndIncrement();
    private final String name;

    private int left;
    private int top;
    private int right;
    private int bottom;

    private float translationX;
    private float translationY;
    private float translationZ;
    private float elevation;
    private float rotationZ;
    private float rotationX;
    private float rotationY;
    private float scaleX = 1.0f;
    private float scaleY = 1.0f;
    private float pivotX;
    private float pivotY;
    private boolean pivotExplicitlySet;
    private float cameraDistance = 8.0f;

    private float alpha = 1.0f;
    private boolean clipToBounds = true;
    private Rect clipRect;
    private boolean clipToOutline;
    private Outline outline;
    private boolean useCompositingLayer;
    private Paint layerPaint;
    private RenderEffect renderEffect;
    private boolean projectBackwards;
    private boolean projectionReceiver;
    private boolean overlappingRendering = true;
    private int spotShadowColor = Color.BLACK;
    private int ambientShadowColor = Color.BLACK;
    private boolean forceDarkAllowed = true;

    private RecordingCanvas activeCanvas;
    private Bitmap displayBitmap;
    private boolean hasDisplayList;

    public RenderNode(String name) {
        this.name = name;
    }

    public RecordingCanvas beginRecording(int width, int height) {
        if (activeCanvas != null) {
            throw new IllegalStateException("Recording currently in progress — missing endRecording()?");
        }
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Recording dimensions must be non-negative");
        }
        int bw = Math.max(1, Math.min(4096, width));
        int bh = Math.max(1, Math.min(4096, height));
        Bitmap target = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        activeCanvas = new RecordingCanvas(this, target, width, height);
        return activeCanvas;
    }

    public RecordingCanvas beginRecording() {
        return beginRecording(getWidth(), getHeight());
    }

    public void endRecording() {
        if (activeCanvas == null) {
            throw new IllegalStateException("No recording in progress — missing beginRecording()?");
        }
        displayBitmap = activeCanvas.shirazFinish();
        activeCanvas = null;
        hasDisplayList = true;
    }

    public void discardDisplayList() {
        if (activeCanvas != null) {
            activeCanvas.shirazFinish();
            activeCanvas = null;
        }
        if (displayBitmap != null) {
            displayBitmap.recycle();
            displayBitmap = null;
        }
        hasDisplayList = false;
    }

    public boolean hasDisplayList() {
        return hasDisplayList && displayBitmap != null && !displayBitmap.isRecycled();
    }

    Bitmap shirazDisplayBitmap() {
        return displayBitmap;
    }

    RenderEffect shirazRenderEffect() {
        return renderEffect;
    }

    Rect shirazClipRect() {
        return clipRect;
    }

    Outline shirazOutline() {
        return outline;
    }

    Paint shirazLayerPaint() {
        return layerPaint;
    }

    public boolean hasIdentityMatrix() {
        return translationX == 0f
                && translationY == 0f
                && rotationZ == 0f
                && rotationX == 0f
                && rotationY == 0f
                && scaleX == 1f
                && scaleY == 1f;
    }

    public void getMatrix(Matrix outMatrix) {
        if (outMatrix == null) {
            throw new NullPointerException("outMatrix");
        }
        outMatrix.reset();
        if (hasIdentityMatrix()) {
            return;
        }
        float px = getPivotX();
        float py = getPivotY();
        outMatrix.postTranslate(-px, -py);
        outMatrix.postScale(scaleX, scaleY);
        if (rotationX != 0f || rotationY != 0f) {
            Camera cam = new Camera();
            cam.setLocation(0f, 0f, -Math.abs(cameraDistance));
            cam.rotateX(rotationX);
            cam.rotateY(rotationY);
            Matrix m3d = new Matrix();
            cam.getMatrix(m3d);
            if (m3d.isAffine()) {
                outMatrix.postConcat(m3d);
            }
        }
        outMatrix.postRotate(rotationZ);
        outMatrix.postTranslate(px + translationX, py + translationY);
    }

    public void getInverseMatrix(Matrix outMatrix) {
        if (outMatrix == null) {
            throw new NullPointerException("outMatrix");
        }
        Matrix forward = new Matrix();
        getMatrix(forward);
        if (!forward.invert(outMatrix)) {
            outMatrix.reset();
        }
    }

    public boolean setUseCompositingLayer(boolean forceToLayer, Paint paint) {
        boolean changed = (this.useCompositingLayer != forceToLayer) || (this.layerPaint != paint);
        this.useCompositingLayer = forceToLayer;
        this.layerPaint = paint == null ? null : new Paint(paint);
        return changed;
    }

    public boolean getUseCompositingLayer() {
        return useCompositingLayer;
    }

    public boolean setClipRect(Rect rect) {
        if (rect == null) {
            boolean had = this.clipRect != null;
            this.clipRect = null;
            return had;
        }
        if (rect.equals(this.clipRect)) {
            return false;
        }
        this.clipRect = new Rect(rect);
        return true;
    }

    public boolean setClipToBounds(boolean clipToBounds) {
        if (this.clipToBounds == clipToBounds) return false;
        this.clipToBounds = clipToBounds;
        return true;
    }

    public boolean getClipToBounds() {
        return clipToBounds;
    }

    public boolean setProjectBackwards(boolean shouldProject) {
        if (this.projectBackwards == shouldProject) return false;
        this.projectBackwards = shouldProject;
        return true;
    }

    public boolean setProjectionReceiver(boolean shouldRecieve) {
        if (this.projectionReceiver == shouldRecieve) return false;
        this.projectionReceiver = shouldRecieve;
        return true;
    }

    public boolean setOutline(Outline outline) {
        if (outline == null) {
            boolean had = this.outline != null;
            this.outline = null;
            return had;
        }
        Outline copy = new Outline(outline);
        this.outline = copy;
        return true;
    }

    public boolean hasShadow() {
        return (translationZ + elevation) > 0.0f && outline != null && outline.getAlpha() > 0.0f;
    }

    public boolean setSpotShadowColor(int color) {
        if (this.spotShadowColor == color) return false;
        this.spotShadowColor = color;
        return true;
    }

    public int getSpotShadowColor() {
        return spotShadowColor;
    }

    public boolean setAmbientShadowColor(int color) {
        if (this.ambientShadowColor == color) return false;
        this.ambientShadowColor = color;
        return true;
    }

    public int getAmbientShadowColor() {
        return ambientShadowColor;
    }

    public boolean setClipToOutline(boolean clipToOutline) {
        if (this.clipToOutline == clipToOutline) return false;
        this.clipToOutline = clipToOutline;
        return true;
    }

    public boolean getClipToOutline() {
        return clipToOutline;
    }

    public boolean setAlpha(float alpha) {
        if (Float.compare(this.alpha, alpha) == 0) return false;
        this.alpha = alpha;
        return true;
    }

    public boolean setRenderEffect(RenderEffect renderEffect) {
        if (this.renderEffect == renderEffect) return false;
        this.renderEffect = renderEffect;
        return true;
    }

    public float getAlpha() {
        return alpha;
    }

    public boolean setHasOverlappingRendering(boolean overlappingRendering) {
        if (this.overlappingRendering == overlappingRendering) return false;
        this.overlappingRendering = overlappingRendering;
        return true;
    }

    public boolean hasOverlappingRendering() {
        return overlappingRendering;
    }

    public boolean setElevation(float lift) {
        if (Float.compare(this.elevation, lift) == 0) return false;
        this.elevation = lift;
        return true;
    }

    public float getElevation() {
        return elevation;
    }

    public boolean setTranslationX(float translationX) {
        if (Float.compare(this.translationX, translationX) == 0) return false;
        this.translationX = translationX;
        return true;
    }

    public float getTranslationX() {
        return translationX;
    }

    public boolean setTranslationY(float translationY) {
        if (Float.compare(this.translationY, translationY) == 0) return false;
        this.translationY = translationY;
        return true;
    }

    public float getTranslationY() {
        return translationY;
    }

    public boolean setTranslationZ(float translationZ) {
        if (Float.compare(this.translationZ, translationZ) == 0) return false;
        this.translationZ = translationZ;
        return true;
    }

    public float getTranslationZ() {
        return translationZ;
    }

    public boolean setRotationZ(float rotation) {
        if (Float.compare(this.rotationZ, rotation) == 0) return false;
        this.rotationZ = rotation;
        return true;
    }

    public float getRotationZ() {
        return rotationZ;
    }

    public boolean setRotationX(float rotationX) {
        if (Float.compare(this.rotationX, rotationX) == 0) return false;
        this.rotationX = rotationX;
        return true;
    }

    public float getRotationX() {
        return rotationX;
    }

    public boolean setRotationY(float rotationY) {
        if (Float.compare(this.rotationY, rotationY) == 0) return false;
        this.rotationY = rotationY;
        return true;
    }

    public float getRotationY() {
        return rotationY;
    }

    public boolean setScaleX(float scaleX) {
        if (Float.compare(this.scaleX, scaleX) == 0) return false;
        this.scaleX = scaleX;
        return true;
    }

    public float getScaleX() {
        return scaleX;
    }

    public boolean setScaleY(float scaleY) {
        if (Float.compare(this.scaleY, scaleY) == 0) return false;
        this.scaleY = scaleY;
        return true;
    }

    public float getScaleY() {
        return scaleY;
    }

    public boolean setPivotX(float pivotX) {
        boolean changed = !pivotExplicitlySet || Float.compare(this.pivotX, pivotX) != 0;
        this.pivotX = pivotX;
        this.pivotExplicitlySet = true;
        return changed;
    }

    public float getPivotX() {
        return pivotExplicitlySet ? pivotX : getWidth() / 2.0f;
    }

    public boolean setPivotY(float pivotY) {
        boolean changed = !pivotExplicitlySet || Float.compare(this.pivotY, pivotY) != 0;
        this.pivotY = pivotY;
        this.pivotExplicitlySet = true;
        return changed;
    }

    public float getPivotY() {
        return pivotExplicitlySet ? pivotY : getHeight() / 2.0f;
    }

    public boolean isPivotExplicitlySet() {
        return pivotExplicitlySet;
    }

    public boolean resetPivot() {
        if (!pivotExplicitlySet) return false;
        pivotExplicitlySet = false;
        pivotX = 0f;
        pivotY = 0f;
        return true;
    }

    public boolean setCameraDistance(float distance) {
        if (!Float.isFinite(distance) || distance < 0.0f) {
            throw new IllegalArgumentException("distance must be finite and >= 0, got " + distance);
        }
        if (Float.compare(this.cameraDistance, distance) == 0) return false;
        this.cameraDistance = distance;
        return true;
    }

    public float getCameraDistance() {
        return cameraDistance;
    }

    public int getLeft() {
        return left;
    }

    public int getTop() {
        return top;
    }

    public int getRight() {
        return right;
    }

    public int getBottom() {
        return bottom;
    }

    public int getWidth() {
        return right - left;
    }

    public int getHeight() {
        return bottom - top;
    }

    public boolean setPosition(int left, int top, int right, int bottom) {
        if (this.left == left && this.top == top && this.right == right && this.bottom == bottom) {
            return false;
        }
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        return true;
    }

    public boolean setPosition(Rect position) {
        if (position == null) {
            throw new NullPointerException("position");
        }
        return setPosition(position.left, position.top, position.right, position.bottom);
    }

    public boolean offsetLeftAndRight(int offset) {
        if (offset == 0) return false;
        left += offset;
        right += offset;
        return true;
    }

    public boolean offsetTopAndBottom(int offset) {
        if (offset == 0) return false;
        top += offset;
        bottom += offset;
        return true;
    }

    public long computeApproximateMemoryUsage() {
        return displayBitmap != null && !displayBitmap.isRecycled()
                ? displayBitmap.getAllocationByteCount() + 256L
                : 256L;
    }

    public boolean setForceDarkAllowed(boolean allow) {
        if (this.forceDarkAllowed == allow) return false;
        this.forceDarkAllowed = allow;
        return true;
    }

    public boolean isForceDarkAllowed() {
        return forceDarkAllowed;
    }

    public long getUniqueId() {
        return uniqueId;
    }
}
