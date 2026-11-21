// SPDX-License-Identifier: GPL-3.0-only
package android.widget;

import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.view.View;
import java.util.Objects;

/** Magnifier widget and Builder for inspecting zoomed regions of a View. */
public final class Magnifier {
    public static final int SOURCE_BOUND_MAX_IN_SURFACE = 0;
    public static final int SOURCE_BOUND_MAX_VISIBLE = 1;

    private final View mView;
    private final int mWindowWidth;
    private final int mWindowHeight;
    private float mZoom;
    private final float mWindowElevation;
    private final float mWindowCornerRadius;
    private final Drawable mOverlay;
    private final int mDefaultHorizontalSourceToMagnifierOffset;
    private final int mDefaultVerticalSourceToMagnifierOffset;
    private final boolean mClippingEnabled;
    private boolean mShowing;
    private Point mPosition;
    private Point mSourcePosition;

    @Deprecated
    public Magnifier(View view) {
        this(new Builder(view));
    }

    Magnifier(Builder params) {
        mView = params.mView;
        mWindowWidth = params.mWidth;
        mWindowHeight = params.mHeight;
        mZoom = params.mZoom;
        mWindowElevation = params.mElevation;
        mWindowCornerRadius = params.mCornerRadius;
        mOverlay = params.mOverlay;
        mDefaultHorizontalSourceToMagnifierOffset = params.mHorizontalDefaultSourceToMagnifierOffset;
        mDefaultVerticalSourceToMagnifierOffset = params.mVerticalDefaultSourceToMagnifierOffset;
        mClippingEnabled = params.mIsFishEyeStyle ? false : params.mClippingEnabled;
    }

    public void show(float sourceCenterX, float sourceCenterY) {
        show(
                sourceCenterX,
                sourceCenterY,
                sourceCenterX + mDefaultHorizontalSourceToMagnifierOffset,
                sourceCenterY + mDefaultVerticalSourceToMagnifierOffset);
    }

    public void show(
            float sourceCenterX,
            float sourceCenterY,
            float magnifierCenterX,
            float magnifierCenterY) {
        int srcW = getSourceWidth();
        int srcH = getSourceHeight();
        mSourcePosition = new Point(
                Math.round(sourceCenterX - srcW / 2f),
                Math.round(sourceCenterY - srcH / 2f));
        mPosition = new Point(
                Math.round(magnifierCenterX - mWindowWidth / 2f),
                Math.round(magnifierCenterY - mWindowHeight / 2f));
        mShowing = true;
    }

    public void dismiss() {
        mShowing = false;
        mPosition = null;
        mSourcePosition = null;
    }

    public void update() {
    }

    public int getWidth() {
        return mWindowWidth;
    }

    public int getHeight() {
        return mWindowHeight;
    }

    public int getSourceWidth() {
        return Math.round(mWindowWidth / mZoom);
    }

    public int getSourceHeight() {
        return Math.round(mWindowHeight / mZoom);
    }

    public void setZoom(float zoom) {
        if (zoom <= 0f) {
            throw new IllegalArgumentException("Zoom should be positive");
        }
        mZoom = zoom;
    }

    public float getZoom() {
        return mZoom;
    }

    public float getElevation() {
        return mWindowElevation;
    }

    public float getCornerRadius() {
        return mWindowCornerRadius;
    }

    public int getDefaultHorizontalSourceToMagnifierOffset() {
        return mDefaultHorizontalSourceToMagnifierOffset;
    }

    public int getDefaultVerticalSourceToMagnifierOffset() {
        return mDefaultVerticalSourceToMagnifierOffset;
    }

    public Drawable getOverlay() {
        return mOverlay;
    }

    public boolean isClippingEnabled() {
        return mClippingEnabled;
    }

    public Point getPosition() {
        return mShowing && mPosition != null ? new Point(mPosition) : null;
    }

    public Point getSourcePosition() {
        return mShowing && mSourcePosition != null ? new Point(mSourcePosition) : null;
    }

    public static final class Builder {
        private final View mView;
        private int mWidth;
        private int mHeight;
        private float mElevation;
        private float mZoom;
        private float mCornerRadius;
        private Drawable mOverlay;
        private int mHorizontalDefaultSourceToMagnifierOffset;
        private int mVerticalDefaultSourceToMagnifierOffset;
        private boolean mClippingEnabled;
        private int mLeftContentBound;
        private int mTopContentBound;
        private int mRightContentBound;
        private int mBottomContentBound;
        private boolean mIsFishEyeStyle;

        public Builder(View view) {
            mView = Objects.requireNonNull(view);
            float density = view.getContext().getResources().getDisplayMetrics().density;
            mWidth = Math.round(100f * density);
            mHeight = Math.round(48f * density);
            mElevation = 4f * density;
            mCornerRadius = 2f * density;
            mZoom = 1.25f;
            mHorizontalDefaultSourceToMagnifierOffset = 0;
            mVerticalDefaultSourceToMagnifierOffset = -Math.round(42f * density);
            mClippingEnabled = true;
            mLeftContentBound = SOURCE_BOUND_MAX_VISIBLE;
            mTopContentBound = SOURCE_BOUND_MAX_IN_SURFACE;
            mRightContentBound = SOURCE_BOUND_MAX_VISIBLE;
            mBottomContentBound = SOURCE_BOUND_MAX_IN_SURFACE;
        }

        public Builder setSize(int width, int height) {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("Magnifier size must be positive");
            }
            mWidth = width;
            mHeight = height;
            return this;
        }

        public Builder setInitialZoom(float zoom) {
            if (zoom <= 0f) {
                throw new IllegalArgumentException("Zoom should be positive");
            }
            mZoom = zoom;
            return this;
        }

        public Builder setElevation(float elevation) {
            if (elevation < 0f) {
                throw new IllegalArgumentException("Elevation should be non-negative");
            }
            mElevation = elevation;
            return this;
        }

        public Builder setCornerRadius(float cornerRadius) {
            if (cornerRadius < 0f) {
                throw new IllegalArgumentException("Corner radius should be non-negative");
            }
            mCornerRadius = cornerRadius;
            return this;
        }

        public Builder setOverlay(Drawable overlay) {
            mOverlay = overlay;
            return this;
        }

        public Builder setDefaultSourceToMagnifierOffset(int horizontalOffset, int verticalOffset) {
            mHorizontalDefaultSourceToMagnifierOffset = horizontalOffset;
            mVerticalDefaultSourceToMagnifierOffset = verticalOffset;
            return this;
        }

        public Builder setClippingEnabled(boolean clip) {
            mClippingEnabled = clip;
            return this;
        }

        public Builder setSourceBounds(int left, int top, int right, int bottom) {
            mLeftContentBound = left;
            mTopContentBound = top;
            mRightContentBound = right;
            mBottomContentBound = bottom;
            return this;
        }

        public Magnifier build() {
            return new Magnifier(this);
        }
    }
}
