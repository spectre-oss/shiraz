// SPDX-License-Identifier: GPL-3.0-only
package android.transition;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/** Alpha-fade {@link Visibility} transition animating {@link View#setTransitionAlpha(float)}. */
public class Fade extends Visibility {
    private static final String PROPNAME_TRANSITION_ALPHA = "android:fade:transitionAlpha";

    public static final int IN = Visibility.MODE_IN;
    public static final int OUT = Visibility.MODE_OUT;

    public Fade() {}

    public Fade(int fadingMode) {
        setMode(fadingMode);
    }

    public Fade(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void captureStartValues(TransitionValues transitionValues) {
        super.captureStartValues(transitionValues);
        if (transitionValues.view != null) {
            transitionValues.values.put(
                    PROPNAME_TRANSITION_ALPHA,
                    Float.valueOf(transitionValues.view.getTransitionAlpha()));
        }
    }

    private Animator createAnimation(final View view, float startAlpha, float endAlpha) {
        if (startAlpha == endAlpha) {
            return null;
        }
        view.setTransitionAlpha(startAlpha);
        ObjectAnimator anim = ObjectAnimator.ofFloat(view, "transitionAlpha", startAlpha, endAlpha);
        anim.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        view.setTransitionAlpha(1f);
                    }
                });
        addListener(
                new TransitionListenerAdapter() {
                    @Override
                    public void onTransitionEnd(Transition transition) {
                        view.setTransitionAlpha(1f);
                        transition.removeListener(this);
                    }
                });
        return anim;
    }

    @Override
    public Animator onAppear(
            ViewGroup sceneRoot,
            View view,
            TransitionValues startValues,
            TransitionValues endValues) {
        float startAlpha = getStartAlpha(startValues, 0f);
        if (startAlpha == 1f) {
            startAlpha = 0f;
        }
        return createAnimation(view, startAlpha, 1f);
    }

    @Override
    public Animator onDisappear(
            ViewGroup sceneRoot,
            View view,
            TransitionValues startValues,
            TransitionValues endValues) {
        float startAlpha = getStartAlpha(startValues, 1f);
        return createAnimation(view, startAlpha, 0f);
    }

    private static float getStartAlpha(TransitionValues startValues, float fallbackValue) {
        if (startValues != null) {
            Float startAlphaFloat = (Float) startValues.values.get(PROPNAME_TRANSITION_ALPHA);
            if (startAlphaFloat != null) {
                return startAlphaFloat.floatValue();
            }
        }
        return fallbackValue;
    }
}
