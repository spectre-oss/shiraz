// SPDX-License-Identifier: GPL-3.0-only
package android.widget.inline;

import android.content.Context;
import android.graphics.Rect;
import android.view.SurfaceControl;
import android.view.ViewGroup;

public class InlineContentView extends ViewGroup {
    public interface SurfaceControlCallback {
        void onCreated(SurfaceControl surfaceControl);
        void onDestroyed(SurfaceControl surfaceControl);
    }

    private final SurfaceControl mSurfaceControl;
    private SurfaceControlCallback mSurfaceControlCallback;
    private boolean mZOrderedOnTop;

    public InlineContentView(Context context) {
        this(context, null);
    }

    public InlineContentView(Context context, SurfaceControl surfaceControl) {
        super(context);
        mSurfaceControl = surfaceControl != null
                ? surfaceControl
                : new SurfaceControl.Builder().setName("InlineContentView").build();
    }

    public SurfaceControl getSurfaceControl() {
        return mSurfaceControl;
    }

    @Override
    public void setClipBounds(Rect clipBounds) {
        super.setClipBounds(clipBounds);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mSurfaceControlCallback != null && mSurfaceControl != null) {
            mSurfaceControlCallback.onCreated(mSurfaceControl);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mSurfaceControlCallback != null && mSurfaceControl != null) {
            mSurfaceControlCallback.onDestroyed(mSurfaceControl);
        }
        super.onDetachedFromWindow();
    }

    @Override
    public void onLayout(boolean changed, int l, int t, int r, int b) {
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            getChildAt(i).layout(0, 0, r - l, b - t);
        }
    }

    public void setSurfaceControlCallback(SurfaceControlCallback callback) {
        mSurfaceControlCallback = callback;
    }

    public boolean isZOrderedOnTop() {
        return mZOrderedOnTop;
    }

    public boolean setZOrderedOnTop(boolean onTop) {
        mZOrderedOnTop = onTop;
        return true;
    }
}
