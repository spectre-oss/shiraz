// SPDX-License-Identifier: GPL-3.0-only
package android.widget;

import android.content.Context;
import android.util.AttributeSet;

/** ViewAnimator that periodically flips between child views at a configurable interval. */
@RemoteViews.RemoteView
public class ViewFlipper extends ViewAnimator {
    private static final int DEFAULT_INTERVAL = 3000;

    private int mFlipInterval = DEFAULT_INTERVAL;
    private boolean mAutoStart = false;
    private boolean mRunning = false;
    private boolean mStarted = false;
    private boolean mVisible = false;

    private final Runnable mFlipRunnable = new Runnable() {
        @Override
        public void run() {
            if (mRunning) {
                showNext();
                postDelayed(mFlipRunnable, mFlipInterval);
            }
        }
    };

    public ViewFlipper(Context context) {
        super(context);
    }

    public ViewFlipper(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (attrs != null) {
            shiraz.runtime.XmlAttributes a = new shiraz.runtime.XmlAttributes(context, attrs);
            mFlipInterval = a.integer("flipInterval", DEFAULT_INTERVAL);
            mAutoStart = a.bool("autoStart", false);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mAutoStart) {
            startFlipping();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mVisible = false;
        updateRunning(false);
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        mVisible = visibility == VISIBLE;
        updateRunning(false);
    }

    public void setFlipInterval(int milliseconds) {
        mFlipInterval = milliseconds;
    }

    public int getFlipInterval() {
        return mFlipInterval;
    }

    public void startFlipping() {
        mStarted = true;
        updateRunning();
    }

    public void stopFlipping() {
        mStarted = false;
        updateRunning();
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return ViewFlipper.class.getName();
    }

    private void updateRunning() {
        updateRunning(true);
    }

    private void updateRunning(boolean flipNow) {
        boolean running = mVisible && mStarted;
        if (running != mRunning) {
            if (running) {
                setDisplayedChild(getDisplayedChild());
                postDelayed(mFlipRunnable, mFlipInterval);
            } else {
                removeCallbacks(mFlipRunnable);
            }
            mRunning = running;
        }
    }

    public boolean isFlipping() {
        return mStarted;
    }

    public void setAutoStart(boolean autoStart) {
        mAutoStart = autoStart;
    }

    public boolean isAutoStart() {
        return mAutoStart;
    }
}
