// SPDX-License-Identifier: GPL-3.0-only
package android.transition;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/** Captures and animates {@link View#getScrollX()} and {@link View#getScrollY()} changes. */
public class ChangeScroll extends Transition {
    private static final String PROPNAME_SCROLL_X = "android:changeScroll:x";
    private static final String PROPNAME_SCROLL_Y = "android:changeScroll:y";
    private static final String[] PROPERTIES = {PROPNAME_SCROLL_X, PROPNAME_SCROLL_Y};

    public ChangeScroll() {}

    public ChangeScroll(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void captureStartValues(TransitionValues transitionValues) {
        captureScrollValues(transitionValues);
    }

    @Override
    public void captureEndValues(TransitionValues transitionValues) {
        captureScrollValues(transitionValues);
    }

    @Override
    public String[] getTransitionProperties() {
        return PROPERTIES.clone();
    }

    private void captureScrollValues(TransitionValues transitionValues) {
        if (transitionValues.view != null) {
            transitionValues.values.put(
                    PROPNAME_SCROLL_X, Integer.valueOf(transitionValues.view.getScrollX()));
            transitionValues.values.put(
                    PROPNAME_SCROLL_Y, Integer.valueOf(transitionValues.view.getScrollY()));
        }
    }

    @Override
    public Animator createAnimator(
            ViewGroup sceneRoot, TransitionValues startValues, TransitionValues endValues) {
        if (startValues == null || endValues == null) {
            return null;
        }
        View view = endValues.view;
        int startX = ((Integer) startValues.values.get(PROPNAME_SCROLL_X)).intValue();
        int endX = ((Integer) endValues.values.get(PROPNAME_SCROLL_X)).intValue();
        int startY = ((Integer) startValues.values.get(PROPNAME_SCROLL_Y)).intValue();
        int endY = ((Integer) endValues.values.get(PROPNAME_SCROLL_Y)).intValue();
        Animator scrollXAnimator = null;
        Animator scrollYAnimator = null;
        if (startX != endX) {
            view.setScrollX(startX);
            scrollXAnimator = ObjectAnimator.ofInt(view, "scrollX", startX, endX);
        }
        if (startY != endY) {
            view.setScrollY(startY);
            scrollYAnimator = ObjectAnimator.ofInt(view, "scrollY", startY, endY);
        }
        if (scrollXAnimator != null && scrollYAnimator != null) {
            AnimatorSet set = new AnimatorSet();
            set.playTogether(scrollXAnimator, scrollYAnimator);
            return set;
        }
        return scrollXAnimator != null ? scrollXAnimator : scrollYAnimator;
    }
}
