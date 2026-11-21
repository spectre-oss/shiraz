// SPDX-License-Identifier: GPL-3.0-only
package android.view;

import android.graphics.Canvas;
import java.util.ArrayList;

public class ViewGroupOverlay extends ViewOverlay {
    private final ViewGroup mHostGroup;
    private final ArrayList<View> mOverlayViews = new ArrayList<View>();

    ViewGroupOverlay(ViewGroup host) {
        super(host);
        mHostGroup = host;
    }

    public void add(View view) {
        if (view == null) {
            throw new IllegalArgumentException("view must not be null");
        }
        if (view.getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) view.getParent();
            if (parent != mHostGroup && parent.getParent() != null) {
                int[] parentLoc = new int[2];
                int[] hostLoc = new int[2];
                parent.getLocationInWindow(parentLoc);
                mHostGroup.getLocationInWindow(hostLoc);
                view.offsetLeftAndRight(parentLoc[0] - hostLoc[0]);
                view.offsetTopAndBottom(parentLoc[1] - hostLoc[1]);
            }
            parent.removeView(view);
        }
        if (!mOverlayViews.contains(view)) {
            mOverlayViews.add(view);
            mHostGroup.invalidate();
        }
    }

    public void remove(View view) {
        if (view == null) {
            throw new IllegalArgumentException("view must not be null");
        }
        if (mOverlayViews.remove(view)) {
            mHostGroup.invalidate();
        }
    }

    @Override
    public void clear() {
        super.clear();
        if (!mOverlayViews.isEmpty()) {
            mOverlayViews.clear();
            mHostGroup.invalidate();
        }
    }

    @Override
    void shirazDrawOverlayViews(Canvas canvas) {
        if (mOverlayViews.isEmpty()) {
            return;
        }
        ArrayList<View> snapshot = new ArrayList<View>(mOverlayViews);
        for (int i = 0; i < snapshot.size(); i++) {
            View v = snapshot.get(i);
            if (v.getVisibility() == View.VISIBLE) {
                int saved = canvas.save();
                try {
                    canvas.translate(v.getLeft(), v.getTop());
                    canvas.concat(v.getMatrix());
                    v.draw(canvas);
                } finally {
                    canvas.restoreToCount(saved);
                }
            }
        }
    }
}
