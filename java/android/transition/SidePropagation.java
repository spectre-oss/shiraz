// SPDX-License-Identifier: GPL-3.0-only
package android.transition;

import android.graphics.Rect;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

/** Directional Manhattan-distance start-delay propagation from a gravity edge. */
public class SidePropagation extends VisibilityPropagation {
    private float propagationSpeed = 3.0f;
    private int side = Gravity.BOTTOM;

    public SidePropagation() {}

    public void setSide(int side) {
        this.side = side;
    }

    public void setPropagationSpeed(float propagationSpeed) {
        if (propagationSpeed == 0f) {
            throw new IllegalArgumentException("propagationSpeed may not be 0");
        }
        this.propagationSpeed = propagationSpeed;
    }

    @Override
    public long getStartDelay(
            ViewGroup sceneRoot,
            Transition transition,
            TransitionValues startValues,
            TransitionValues endValues) {
        if (startValues == null && endValues == null) {
            return 0L;
        }
        int directionMultiplier = 1;
        Rect epicenter = transition != null ? transition.getEpicenter() : null;
        TransitionValues positionValues;
        if (endValues == null || getViewVisibility(startValues) == View.VISIBLE) {
            positionValues = startValues;
            directionMultiplier = -1;
        } else {
            positionValues = endValues;
        }
        int viewCenterX = getViewX(positionValues);
        int viewCenterY = getViewY(positionValues);
        int left = 0;
        int top = 0;
        int right = 0;
        int bottom = 0;
        if (sceneRoot != null) {
            int[] loc = new int[2];
            sceneRoot.getLocationOnScreen(loc);
            left = loc[0] + Math.round(sceneRoot.getTranslationX());
            top = loc[1] + Math.round(sceneRoot.getTranslationY());
            right = left + sceneRoot.getWidth();
            bottom = top + sceneRoot.getHeight();
        }
        int epicenterX = epicenter != null ? epicenter.centerX() : (left + right) / 2;
        int epicenterY = epicenter != null ? epicenter.centerY() : (top + bottom) / 2;
        int resolvedSide = resolveSide(sceneRoot);
        int distance;
        switch (resolvedSide) {
            case Gravity.LEFT:
                distance = right - viewCenterX + Math.abs(epicenterY - viewCenterY);
                break;
            case Gravity.TOP:
                distance = bottom - viewCenterY + Math.abs(epicenterX - viewCenterX);
                break;
            case Gravity.RIGHT:
                distance = viewCenterX - left + Math.abs(epicenterY - viewCenterY);
                break;
            case Gravity.BOTTOM:
            default:
                distance = viewCenterY - top + Math.abs(epicenterX - viewCenterX);
                break;
        }
        int maxDistance = (resolvedSide == Gravity.LEFT || resolvedSide == Gravity.RIGHT)
                ? (right - left)
                : (bottom - top);
        if (maxDistance <= 0) {
            maxDistance = Math.max(1, distance);
        }
        float distanceFraction = ((float) distance) / maxDistance;
        long duration = transition != null ? transition.getDuration() : -1L;
        if (duration < 0L) {
            duration = 300L;
        }
        return Math.round(duration * directionMultiplier / propagationSpeed * distanceFraction);
    }

    private int resolveSide(View sceneRoot) {
        boolean isRtl = sceneRoot != null && sceneRoot.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        if (side == Gravity.START) {
            return isRtl ? Gravity.RIGHT : Gravity.LEFT;
        } else if (side == Gravity.END) {
            return isRtl ? Gravity.LEFT : Gravity.RIGHT;
        }
        return side;
    }
}
