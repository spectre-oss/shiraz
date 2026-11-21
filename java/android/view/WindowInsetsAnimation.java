// SPDX-License-Identifier: GPL-3.0-only
package android.view;

import android.graphics.Insets;
import android.view.animation.Interpolator;
import java.util.List;

public final class WindowInsetsAnimation {
    private final int mTypeMask;
    private float mFraction;
    private final Interpolator mInterpolator;
    private final long mDurationMillis;
    private float mAlpha = 1f;

    public WindowInsetsAnimation(int typeMask, Interpolator interpolator, long durationMillis) {
        mTypeMask = typeMask;
        mInterpolator = interpolator;
        mDurationMillis = durationMillis;
    }

    public int getTypeMask() {
        return mTypeMask;
    }

    public float getFraction() {
        return mFraction;
    }

    public float getInterpolatedFraction() {
        if (mInterpolator != null) {
            return mInterpolator.getInterpolation(mFraction);
        }
        return mFraction;
    }

    public Interpolator getInterpolator() {
        return mInterpolator;
    }

    public long getDurationMillis() {
        return mDurationMillis;
    }

    public void setFraction(float fraction) {
        mFraction = fraction;
    }

    public float getAlpha() {
        return mAlpha;
    }

    public void setAlpha(float alpha) {
        mAlpha = alpha;
    }

    public static final class Bounds {
        private final Insets mLowerBound;
        private final Insets mUpperBound;

        public Bounds(Insets lowerBound, Insets upperBound) {
            if (lowerBound == null) {
                throw new NullPointerException("lowerBound must not be null");
            }
            if (upperBound == null) {
                throw new NullPointerException("upperBound must not be null");
            }
            mLowerBound = lowerBound;
            mUpperBound = upperBound;
        }

        public Insets getLowerBound() {
            return mLowerBound;
        }

        public Insets getUpperBound() {
            return mUpperBound;
        }

        public Bounds inset(Insets insets) {
            return new Bounds(
                    WindowInsets.insetInsets(mLowerBound, insets.left, insets.top,
                            insets.right, insets.bottom),
                    WindowInsets.insetInsets(mUpperBound, insets.left, insets.top,
                            insets.right, insets.bottom));
        }

        @Override
        public String toString() {
            return "Bounds{lower=" + mLowerBound + " upper=" + mUpperBound + "}";
        }
    }

    public abstract static class Callback {
        public static final int DISPATCH_MODE_STOP = 0;
        public static final int DISPATCH_MODE_CONTINUE_ON_SUBTREE = 1;

        private final int mDispatchMode;

        public Callback(int dispatchMode) {
            mDispatchMode = dispatchMode;
        }

        public final int getDispatchMode() {
            return mDispatchMode;
        }

        public void onPrepare(WindowInsetsAnimation animation) {
        }

        public Bounds onStart(WindowInsetsAnimation animation, Bounds bounds) {
            return bounds;
        }

        public abstract WindowInsets onProgress(WindowInsets insets,
                List<WindowInsetsAnimation> runningAnimations);

        public void onEnd(WindowInsetsAnimation animation) {
        }
    }
}
