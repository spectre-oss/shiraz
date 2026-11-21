// SPDX-License-Identifier: GPL-3.0-only
package android.transition;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import java.util.Arrays;

/** Captures and animates translation, scale, and 3D rotation changes on target {@link View}s. */
public class ChangeTransform extends Transition {
    private static final String PROPNAME_TRANSFORMS = "android:changeTransform:transforms";
    private static final String PROPNAME_PARENT = "android:changeTransform:parent";
    private static final String[] TRANSITION_PROPERTIES = {
        PROPNAME_TRANSFORMS,
        PROPNAME_PARENT,
    };

    private boolean useOverlay = true;
    private boolean reparent = true;

    public ChangeTransform() {}

    public ChangeTransform(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public boolean getReparentWithOverlay() {
        return useOverlay;
    }

    public void setReparentWithOverlay(boolean reparentWithOverlay) {
        this.useOverlay = reparentWithOverlay;
    }

    public boolean getReparent() {
        return reparent;
    }

    public void setReparent(boolean reparent) {
        this.reparent = reparent;
    }

    @Override
    public String[] getTransitionProperties() {
        return TRANSITION_PROPERTIES.clone();
    }

    private void captureTransformValues(TransitionValues transitionValues) {
        View view = transitionValues.view;
        if (view == null || view.getVisibility() == View.GONE) {
            return;
        }
        transitionValues.values.put(PROPNAME_PARENT, view.getParent());
        float[] state = new float[] {
            view.getTranslationX(),
            view.getTranslationY(),
            view.getTranslationZ(),
            view.getScaleX(),
            view.getScaleY(),
            view.getRotationX(),
            view.getRotationY(),
            view.getRotation(),
        };
        transitionValues.values.put(PROPNAME_TRANSFORMS, state);
    }

    @Override
    public void captureStartValues(TransitionValues transitionValues) {
        captureTransformValues(transitionValues);
    }

    @Override
    public void captureEndValues(TransitionValues transitionValues) {
        captureTransformValues(transitionValues);
    }

    @Override
    public Animator createAnimator(
            ViewGroup sceneRoot, TransitionValues startValues, TransitionValues endValues) {
        if (startValues == null
                || endValues == null
                || !startValues.values.containsKey(PROPNAME_TRANSFORMS)
                || !endValues.values.containsKey(PROPNAME_TRANSFORMS)) {
            return null;
        }
        final float[] start = (float[]) startValues.values.get(PROPNAME_TRANSFORMS);
        final float[] end = (float[]) endValues.values.get(PROPNAME_TRANSFORMS);
        if (Arrays.equals(start, end)) {
            return null;
        }
        final View view = endValues.view;
        applyState(view, start);
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.addUpdateListener(
                new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        float f = ((Float) animation.getAnimatedValue()).floatValue();
                        view.setTranslationX(lerp(start[0], end[0], f));
                        view.setTranslationY(lerp(start[1], end[1], f));
                        view.setTranslationZ(lerp(start[2], end[2], f));
                        view.setScaleX(lerp(start[3], end[3], f));
                        view.setScaleY(lerp(start[4], end[4], f));
                        view.setRotationX(lerp(start[5], end[5], f));
                        view.setRotationY(lerp(start[6], end[6], f));
                        view.setRotation(lerp(start[7], end[7], f));
                    }
                });
        animator.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        applyState(view, end);
                    }
                });
        return animator;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static void applyState(View view, float[] s) {
        view.setTranslationX(s[0]);
        view.setTranslationY(s[1]);
        view.setTranslationZ(s[2]);
        view.setScaleX(s[3]);
        view.setScaleY(s[4]);
        view.setRotationX(s[5]);
        view.setRotationY(s[6]);
        view.setRotation(s[7]);
    }
}
