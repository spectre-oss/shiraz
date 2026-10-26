// SPDX-License-Identifier: GPL-3.0-only
package android.view;

import android.graphics.Rect;

/** Forwards touches that start inside a parent-coordinate rectangle to another view. */
public class TouchDelegate {
    private final View delegateView;
    private final Rect bounds;
    private final Rect slopBounds;
    private boolean delegateTargeted;

    public TouchDelegate(Rect bounds, View delegateView) {
        if (bounds == null || delegateView == null) throw new NullPointerException("touch delegate");
        this.bounds = new Rect(bounds);
        int slop = 8;
        slopBounds = new Rect(bounds);
        slopBounds.inset(-slop, -slop);
        this.delegateView = delegateView;
    }

    public boolean onTouchEvent(MotionEvent event) {
        int x = (int) event.getX();
        int y = (int) event.getY();
        boolean sendToDelegate = false;
        boolean handled = false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                delegateTargeted = bounds.contains(x, y);
                sendToDelegate = delegateTargeted;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_MOVE:
                sendToDelegate = delegateTargeted;
                if (sendToDelegate && !slopBounds.contains(x, y)) {
                    sendToDelegate = false;
                    if (event.getActionMasked() == MotionEvent.ACTION_MOVE) delegateTargeted = false;
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                sendToDelegate = delegateTargeted;
                delegateTargeted = false;
                break;
            default:
                break;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP) delegateTargeted = false;
        if (sendToDelegate) {
            MotionEvent copy = MotionEvent.obtain(event);
            copy.setLocation(delegateView.getWidth() / 2f, delegateView.getHeight() / 2f);
            handled = delegateView.dispatchTouchEvent(copy);
            copy.recycle();
        }
        return handled;
    }
}
