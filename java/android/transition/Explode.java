// SPDX-License-Identifier: GPL-3.0-only
package android.transition;

import android.animation.Animator;
import android.animation.TimeInterpolator;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

/** Radial outward/inward {@link Visibility} transition driven by scene epicenter distance. */
public class Explode extends Visibility {
    private static final TimeInterpolator DECELERATE = new DecelerateInterpolator();
    private static final TimeInterpolator ACCELERATE = new AccelerateInterpolator();
    private static final String PROPNAME_SCREEN_BOUNDS = "android:explode:screenBounds";

    private final int[] tempLoc = new int[2];

    public Explode() {
        setPropagation(new CircularPropagation());
    }

    public Explode(Context context, AttributeSet attrs) {
        super(context, attrs);
        setPropagation(new CircularPropagation());
    }

    private void captureExplodeValues(TransitionValues transitionValues) {
        View view = transitionValues.view;
        view.getLocationOnScreen(tempLoc);
        int left = tempLoc[0];
        int top = tempLoc[1];
        int right = left + view.getWidth();
        int bottom = top + view.getHeight();
        transitionValues.values.put(PROPNAME_SCREEN_BOUNDS, new Rect(left, top, right, bottom));
    }

    @Override
    public void captureStartValues(TransitionValues transitionValues) {
        super.captureStartValues(transitionValues);
        captureExplodeValues(transitionValues);
    }

    @Override
    public void captureEndValues(TransitionValues transitionValues) {
        super.captureEndValues(transitionValues);
        captureExplodeValues(transitionValues);
    }

    @Override
    public Animator onAppear(
            ViewGroup sceneRoot,
            View view,
            TransitionValues startValues,
            TransitionValues endValues) {
        if (endValues == null) {
            return null;
        }
        Rect bounds = (Rect) endValues.values.get(PROPNAME_SCREEN_BOUNDS);
        float endX = view.getTranslationX();
        float endY = view.getTranslationY();
        calculateOut(sceneRoot, bounds, tempLoc);
        float startX = endX + tempLoc[0];
        float startY = endY + tempLoc[1];
        return Slide.createTranslationAnimation(
                view, startX, startY, endX, endY, endX, endY, DECELERATE);
    }

    @Override
    public Animator onDisappear(
            ViewGroup sceneRoot,
            View view,
            TransitionValues startValues,
            TransitionValues endValues) {
        if (startValues == null) {
            return null;
        }
        Rect bounds = (Rect) startValues.values.get(PROPNAME_SCREEN_BOUNDS);
        float startX = view.getTranslationX();
        float startY = view.getTranslationY();
        calculateOut(sceneRoot, bounds, tempLoc);
        float endX = startX + tempLoc[0];
        float endY = startY + tempLoc[1];
        return Slide.createTranslationAnimation(
                view, startX, startY, endX, endY, startX, startY, ACCELERATE);
    }

    private void calculateOut(View sceneRoot, Rect bounds, int[] outVector) {
        int sceneRootX = 0;
        int sceneRootY = 0;
        int width = 100;
        int height = 100;
        if (sceneRoot != null) {
            sceneRoot.getLocationOnScreen(tempLoc);
            sceneRootX = tempLoc[0];
            sceneRootY = tempLoc[1];
            width = Math.max(1, sceneRoot.getWidth());
            height = Math.max(1, sceneRoot.getHeight());
        }
        int focalX;
        int focalY;
        Rect epicenter = getEpicenter();
        if (epicenter == null) {
            focalX = sceneRootX + (width / 2) + Math.round(sceneRoot != null ? sceneRoot.getTranslationX() : 0f);
            focalY = sceneRootY + (height / 2) + Math.round(sceneRoot != null ? sceneRoot.getTranslationY() : 0f);
        } else {
            focalX = epicenter.centerX();
            focalY = epicenter.centerY();
        }
        int centerX = bounds != null ? bounds.centerX() : focalX + 1;
        int centerY = bounds != null ? bounds.centerY() : focalY;
        double xVector = centerX - focalX;
        double yVector = centerY - focalY;
        if (xVector == 0.0 && yVector == 0.0) {
            xVector = 1.0;
            yVector = 0.0;
        }
        double vectorSize = Math.hypot(xVector, yVector);
        xVector /= vectorSize;
        yVector /= vectorSize;
        double maxDistance = Math.hypot(
                Math.max(focalX - sceneRootX, width - (focalX - sceneRootX)),
                Math.max(focalY - sceneRootY, height - (focalY - sceneRootY)));
        outVector[0] = (int) Math.round(maxDistance * xVector);
        outVector[1] = (int) Math.round(maxDistance * yVector);
    }
}
