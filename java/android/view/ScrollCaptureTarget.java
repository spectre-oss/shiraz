// SPDX-License-Identifier: GPL-3.0-only
package android.view;

import android.graphics.Point;
import android.graphics.Rect;

public final class ScrollCaptureTarget {
    private final View mContainingView;
    private final Rect mLocalVisibleRect;
    private final Point mPositionInWindow;
    private final ScrollCaptureCallback mCallback;
    private final int mHint;
    private Rect mScrollBounds;

    public ScrollCaptureTarget(View scrollTarget, Rect localVisibleRect, Point positionInWindow,
            ScrollCaptureCallback callback) {
        if (scrollTarget == null) {
            throw new NullPointerException("scrollTarget must not be null");
        }
        if (localVisibleRect == null) {
            throw new NullPointerException("localVisibleRect must not be null");
        }
        if (positionInWindow == null) {
            throw new NullPointerException("positionInWindow must not be null");
        }
        if (callback == null) {
            throw new NullPointerException("callback must not be null");
        }
        mContainingView = scrollTarget;
        mHint = scrollTarget.getScrollCaptureHint();
        mCallback = callback;
        mLocalVisibleRect = new Rect(localVisibleRect);
        mPositionInWindow = new Point(positionInWindow);
    }

    public int getHint() {
        return mHint;
    }

    public ScrollCaptureCallback getCallback() {
        return mCallback;
    }

    public View getContainingView() {
        return mContainingView;
    }

    public Rect getLocalVisibleRect() {
        return mLocalVisibleRect;
    }

    public Point getPositionInWindow() {
        return mPositionInWindow;
    }

    public Rect getScrollBounds() {
        return mScrollBounds;
    }

    public void setScrollBounds(Rect scrollBounds) {
        if (scrollBounds == null) {
            mScrollBounds = null;
        } else {
            mScrollBounds = new Rect(scrollBounds);
            if (!mScrollBounds.intersect(0, 0,
                    mContainingView.getWidth(), mContainingView.getHeight())) {
                mScrollBounds.setEmpty();
            }
        }
    }

    public void updatePositionInWindow() {
        int[] loc = new int[2];
        mContainingView.getLocationInWindow(loc);
        mPositionInWindow.x = loc[0];
        mPositionInWindow.y = loc[1];
    }

    @Override
    public String toString() {
        return "ScrollCaptureTarget{view=" + mContainingView
                + ", callback=" + mCallback
                + ", scrollBounds=" + mScrollBounds
                + ", localVisibleRect=" + mLocalVisibleRect
                + ", positionInWindow=" + mPositionInWindow + "}";
    }
}
