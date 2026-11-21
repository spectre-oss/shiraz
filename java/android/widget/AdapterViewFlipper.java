// SPDX-License-Identifier: GPL-3.0-only
package android.widget;

import android.content.Context;
import android.util.AttributeSet;

/** AdapterViewAnimator that periodically flips between adapter views. */
@RemoteViews.RemoteView
public class AdapterViewFlipper extends AdapterViewAnimator {
    private static final int DEFAULT_INTERVAL = 10000;

    private int mFlipInterval = DEFAULT_INTERVAL;
    private boolean mAutoStart = false;
    private boolean mRunning = false;
    private boolean mStarted = false;
    private boolean mVisible = false;
    private boolean mAdvancedByHost = false;

    private final Runnable mFlipRunnable = new Runnable() {
        @Override
        public void run() {
            if (mRunning) {
                showNext();
                postDelayed(mFlipRunnable, mFlipInterval);
            }
        }
    };

    public AdapterViewFlipper(Context context) {
        super(context);
    }

    public AdapterViewFlipper(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AdapterViewFlipper(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public AdapterViewFlipper(
            Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        if (attrs != null) {
            shiraz.runtime.XmlAttributes a =
                    new shiraz.runtime.XmlAttributes(context, attrs, defStyleAttr, defStyleRes);
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
        updateRunning();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        mVisible = (visibility == VISIBLE);
        updateRunning(false);
    }

    @Override
    public void setAdapter(Adapter adapter) {
        super.setAdapter(adapter);
        updateRunning();
    }

    public int getFlipInterval() {
        return mFlipInterval;
    }

    public void setFlipInterval(int flipInterval) {
        mFlipInterval = flipInterval;
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
    public void showNext() {
        if (mRunning) {
            removeCallbacks(mFlipRunnable);
            postDelayed(mFlipRunnable, mFlipInterval);
        }
        super.showNext();
    }

    @Override
    public void showPrevious() {
        if (mRunning) {
            removeCallbacks(mFlipRunnable);
            postDelayed(mFlipRunnable, mFlipInterval);
        }
        super.showPrevious();
    }

    private void updateRunning() {
        updateRunning(true);
    }

    private void updateRunning(boolean flipNow) {
        boolean running = !mAdvancedByHost && mVisible && mStarted && getAdapter() != null;
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

    @Override
    public void fyiWillBeAdvancedByHostKThx() {
        mAdvancedByHost = true;
        updateRunning(false);
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return AdapterViewFlipper.class.getName();
    }
}
