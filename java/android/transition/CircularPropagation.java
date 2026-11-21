// SPDX-License-Identifier: GPL-3.0-only
package android.transition;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

/** Radial start-delay propagation proportional to Euclidean distance from the transition epicenter. */
public class CircularPropagation extends VisibilityPropagation {
    private float propagationSpeed = 3.0f;

    public CircularPropagation() {}

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
        TransitionValues positionValues;
        if (endValues == null || getViewVisibility(startValues) == View.VISIBLE) {
            positionValues = startValues;
            directionMultiplier = -1;
        } else {
            positionValues = endValues;
        }
        int viewCenterX = getViewX(positionValues);
        int viewCenterY = getViewY(positionValues);
        Rect epicenter = transition != null ? transition.getEpicenter() : null;
        int epicenterX;
        int epicenterY;
        if (epicenter != null) {
            epicenterX = epicenter.centerX();
            epicenterY = epicenter.centerY();
        } else if (sceneRoot != null) {
            int[] loc = new int[2];
            sceneRoot.getLocationOnScreen(loc);
            epicenterX = Math.round(loc[0] + (sceneRoot.getWidth() / 2f) + sceneRoot.getTranslationX());
            epicenterY = Math.round(loc[1] + (sceneRoot.getHeight() / 2f) + sceneRoot.getTranslationY());
        } else {
            epicenterX = 0;
            epicenterY = 0;
        }
        double distance = Math.hypot(viewCenterX - epicenterX, viewCenterY - epicenterY);
        double maxDistance = sceneRoot != null ? Math.hypot(sceneRoot.getWidth(), sceneRoot.getHeight()) : Math.max(1.0, distance);
        if (maxDistance <= 0.0) {
            maxDistance = 1.0;
        }
        double distanceFraction = distance / maxDistance;
        long duration = transition != null ? transition.getDuration() : -1L;
        if (duration < 0L) {
            duration = 300L;
        }
        return Math.round(duration * directionMultiplier / propagationSpeed * distanceFraction);
    }
}
