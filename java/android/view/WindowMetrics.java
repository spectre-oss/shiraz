// SPDX-License-Identifier: GPL-3.0-only
package android.view;

import android.graphics.Rect;

public final class WindowMetrics {
    private final Rect mBounds;
    private final WindowInsets mWindowInsets;
    private final float mDensity;

    @Deprecated
    public WindowMetrics(Rect bounds, WindowInsets windowInsets) {
        this(bounds, windowInsets, 1.0f);
    }

    public WindowMetrics(Rect bounds, WindowInsets windowInsets, float density) {
        if (bounds == null) {
            throw new NullPointerException("bounds must not be null");
        }
        if (windowInsets == null) {
            throw new NullPointerException("windowInsets must not be null");
        }
        mBounds = new Rect(bounds);
        mWindowInsets = windowInsets;
        mDensity = density;
    }

    public Rect getBounds() {
        return new Rect(mBounds);
    }

    public WindowInsets getWindowInsets() {
        return mWindowInsets;
    }

    public float getDensity() {
        return mDensity;
    }

    @Override
    public String toString() {
        return "WindowMetrics{bounds=" + mBounds
                + ", windowInsets=" + mWindowInsets
                + ", density=" + mDensity + "}";
    }
}
