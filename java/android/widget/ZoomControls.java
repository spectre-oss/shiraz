// SPDX-License-Identifier: GPL-3.0-only
package android.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AlphaAnimation;

/** LinearLayout pair of zoom-out and zoom-in ZoomButtons. */
@Deprecated
public class ZoomControls extends LinearLayout {
    private final ZoomButton mZoomIn;
    private final ZoomButton mZoomOut;

    public ZoomControls(Context context) {
        this(context, null);
    }

    public ZoomControls(Context context, AttributeSet attrs) {
        super(context, attrs);
        setFocusable(false);
        setOrientation(HORIZONTAL);
        mZoomOut = new ZoomButton(context);
        mZoomOut.setContentDescription("-");
        mZoomIn = new ZoomButton(context);
        mZoomIn.setContentDescription("+");
        int size = Math.round(40f * context.getResources().getDisplayMetrics().density);
        addView(mZoomOut, new LinearLayout.LayoutParams(size, size));
        addView(mZoomIn, new LinearLayout.LayoutParams(size, size));
    }

    public void setOnZoomInClickListener(View.OnClickListener listener) {
        mZoomIn.setOnClickListener(listener);
    }

    public void setOnZoomOutClickListener(View.OnClickListener listener) {
        mZoomOut.setOnClickListener(listener);
    }

    public void setZoomSpeed(long speed) {
        mZoomIn.setZoomSpeed(speed);
        mZoomOut.setZoomSpeed(speed);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return true;
    }

    public void show() {
        fade(View.VISIBLE, 0.0f, 1.0f);
    }

    public void hide() {
        fade(View.GONE, 1.0f, 0.0f);
    }

    private void fade(int visibility, float startAlpha, float endAlpha) {
        AlphaAnimation anim = new AlphaAnimation(startAlpha, endAlpha);
        anim.setDuration(500);
        startAnimation(anim);
        setVisibility(visibility);
    }

    public void setIsZoomInEnabled(boolean isEnabled) {
        mZoomIn.setEnabled(isEnabled);
    }

    public void setIsZoomOutEnabled(boolean isEnabled) {
        mZoomOut.setEnabled(isEnabled);
    }

    @Override
    public boolean hasFocus() {
        return mZoomIn.hasFocus() || mZoomOut.hasFocus();
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return ZoomControls.class.getName();
    }
}
