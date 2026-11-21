// SPDX-License-Identifier: GPL-3.0-only
package android.transition;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.RectEvaluator;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/** Captures and animates {@link View#getClipBounds()} changes across scenes. */
public class ChangeClipBounds extends Transition {
    private static final String PROPNAME_CLIP = "android:clipBounds:clip";
    private static final String PROPNAME_BOUNDS = "android:clipBounds:bounds";
    private static final String[] TRANSITION_PROPERTIES = {PROPNAME_CLIP};

    public ChangeClipBounds() {}

    public ChangeClipBounds(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public String[] getTransitionProperties() {
        return TRANSITION_PROPERTIES.clone();
    }

    private void captureClipValues(TransitionValues values) {
        View view = values.view;
        if (view == null || view.getVisibility() == View.GONE) {
            return;
        }
        Rect clip = view.getClipBounds();
        values.values.put(PROPNAME_CLIP, clip);
        if (clip == null) {
            values.values.put(PROPNAME_BOUNDS, new Rect(0, 0, view.getWidth(), view.getHeight()));
        }
    }

    @Override
    public void captureStartValues(TransitionValues transitionValues) {
        captureClipValues(transitionValues);
    }

    @Override
    public void captureEndValues(TransitionValues transitionValues) {
        captureClipValues(transitionValues);
    }

    @Override
    public Animator createAnimator(
            ViewGroup sceneRoot, TransitionValues startValues, TransitionValues endValues) {
        if (startValues == null
                || endValues == null
                || !startValues.values.containsKey(PROPNAME_CLIP)
                || !endValues.values.containsKey(PROPNAME_CLIP)) {
            return null;
        }
        Rect start = (Rect) startValues.values.get(PROPNAME_CLIP);
        Rect end = (Rect) endValues.values.get(PROPNAME_CLIP);
        final boolean endIsNull = end == null;
        if (start == null && end == null) {
            return null;
        }
        if (start == null) {
            start = (Rect) startValues.values.get(PROPNAME_BOUNDS);
        } else if (end == null) {
            end = (Rect) endValues.values.get(PROPNAME_BOUNDS);
        }
        if (start == null || end == null || start.equals(end)) {
            return null;
        }
        final View target = endValues.view;
        target.setClipBounds(start);
        RectEvaluator evaluator = new RectEvaluator(new Rect());
        ObjectAnimator animator = ObjectAnimator.ofObject(target, "clipBounds", evaluator, start, end);
        if (endIsNull) {
            animator.addListener(
                    new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            target.setClipBounds(null);
                        }
                    });
        }
        return animator;
    }
}
