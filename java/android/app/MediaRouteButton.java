// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

public class MediaRouteButton extends View {
    private int mRouteTypes;
    private View.OnClickListener mExtendedSettingsClickListener;

    public MediaRouteButton(Context context) {
        this(context, null);
    }

    public MediaRouteButton(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MediaRouteButton(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public MediaRouteButton(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setClickable(true);
    }

    public int getRouteTypes() {
        return mRouteTypes;
    }

    public void setRouteTypes(int types) {
        mRouteTypes = types;
    }

    public void setExtendedSettingsClickListener(View.OnClickListener listener) {
        mExtendedSettingsClickListener = listener;
    }

    public void showDialog() {
    }

    @Override
    public void setContentDescription(CharSequence contentDescription) {
        super.setContentDescription(contentDescription);
    }

    @Override
    public boolean performClick() {
        boolean handled = super.performClick();
        if (!handled) {
            showDialog();
        }
        return true;
    }

    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        return super.onCreateDrawableState(extraSpace);
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return super.verifyDrawable(who);
    }

    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = resolveSize(Math.max(48, getSuggestedMinimumWidth() + getPaddingLeft() + getPaddingRight()), widthMeasureSpec);
        int height = resolveSize(Math.max(48, getSuggestedMinimumHeight() + getPaddingTop() + getPaddingBottom()), heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
    }
}
