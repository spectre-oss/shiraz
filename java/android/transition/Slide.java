// SPDX-License-Identifier: GPL-3.0-only
package android.transition;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.TimeInterpolator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

/** Edge-sliding {@link Visibility} transition translating target views across scene root bounds. */
public class Slide extends Visibility {
    private static final TimeInterpolator DECELERATE = new DecelerateInterpolator();
    private static final TimeInterpolator ACCELERATE = new AccelerateInterpolator();
    private static final String PROPNAME_SCREEN_POSITION = "android:slide:screenPosition";

    private int slideEdge = Gravity.BOTTOM;

    public Slide() {
        setSlideEdge(Gravity.BOTTOM);
    }

    public Slide(int slideEdge) {
        setSlideEdge(slideEdge);
    }

    public Slide(Context context, AttributeSet attrs) {
        super(context, attrs);
        setSlideEdge(Gravity.BOTTOM);
    }

    private void captureSlideValues(TransitionValues transitionValues) {
        View view = transitionValues.view;
        int[] position = new int[2];
        view.getLocationOnScreen(position);
        transitionValues.values.put(PROPNAME_SCREEN_POSITION, position);
    }

    @Override
    public void captureStartValues(TransitionValues transitionValues) {
        super.captureStartValues(transitionValues);
        captureSlideValues(transitionValues);
    }

    @Override
    public void captureEndValues(TransitionValues transitionValues) {
        super.captureEndValues(transitionValues);
        captureSlideValues(transitionValues);
    }

    public void setSlideEdge(int slideEdge) {
        switch (slideEdge) {
            case Gravity.LEFT:
            case Gravity.TOP:
            case Gravity.RIGHT:
            case Gravity.BOTTOM:
            case Gravity.START:
            case Gravity.END:
                this.slideEdge = slideEdge;
                break;
            default:
                throw new IllegalArgumentException("Invalid slide direction");
        }
        SidePropagation propagation = new SidePropagation();
        propagation.setSide(slideEdge);
        setPropagation(propagation);
    }

    public int getSlideEdge() {
        return slideEdge;
    }

    private float getGoneX(ViewGroup sceneRoot, View view) {
        boolean isRtl = sceneRoot != null && sceneRoot.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        int width = sceneRoot != null && sceneRoot.getWidth() > 0 ? sceneRoot.getWidth() : Math.max(1, view.getWidth());
        int resolved = slideEdge;
        if (resolved == Gravity.START) {
            resolved = isRtl ? Gravity.RIGHT : Gravity.LEFT;
        } else if (resolved == Gravity.END) {
            resolved = isRtl ? Gravity.LEFT : Gravity.RIGHT;
        }
        if (resolved == Gravity.LEFT) {
            return view.getTranslationX() - width;
        } else if (resolved == Gravity.RIGHT) {
            return view.getTranslationX() + width;
        }
        return view.getTranslationX();
    }

    private float getGoneY(ViewGroup sceneRoot, View view) {
        int height = sceneRoot != null && sceneRoot.getHeight() > 0 ? sceneRoot.getHeight() : Math.max(1, view.getHeight());
        if (slideEdge == Gravity.TOP) {
            return view.getTranslationY() - height;
        } else if (slideEdge == Gravity.BOTTOM) {
            return view.getTranslationY() + height;
        }
        return view.getTranslationY();
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
        final float endX = view.getTranslationX();
        final float endY = view.getTranslationY();
        float startX = getGoneX(sceneRoot, view);
        float startY = getGoneY(sceneRoot, view);
        return createTranslationAnimation(view, startX, startY, endX, endY, endX, endY, DECELERATE);
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
        final float startX = view.getTranslationX();
        final float startY = view.getTranslationY();
        float endX = getGoneX(sceneRoot, view);
        float endY = getGoneY(sceneRoot, view);
        return createTranslationAnimation(view, startX, startY, endX, endY, startX, startY, ACCELERATE);
    }

    static Animator createTranslationAnimation(
            final View view,
            float startX,
            float startY,
            float endX,
            float endY,
            final float terminalX,
            final float terminalY,
            TimeInterpolator interpolator) {
        if (startX == endX && startY == endY) {
            return null;
        }
        view.setTranslationX(startX);
        view.setTranslationY(startY);
        ObjectAnimator anim =
                ObjectAnimator.ofPropertyValuesHolder(
                        view,
                        PropertyValuesHolder.ofFloat(View.TRANSLATION_X, startX, endX),
                        PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, startY, endY));
        anim.setInterpolator(interpolator);
        anim.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        view.setTranslationX(terminalX);
                        view.setTranslationY(terminalY);
                    }
                });
        return anim;
    }
}
