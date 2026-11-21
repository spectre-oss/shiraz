// SPDX-License-Identifier: GPL-3.0-only
package android.transition;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.RectEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/** Animates layout bounds (`left`, `top`, `right`, `bottom`) and optional clip bounds of target views. */
public class ChangeBounds extends Transition {
    private static final String PROPNAME_BOUNDS = "android:changeBounds:bounds";
    private static final String PROPNAME_CLIP = "android:changeBounds:clip";
    private static final String PROPNAME_PARENT = "android:changeBounds:parent";
    private static final String PROPNAME_WINDOW_X = "android:changeBounds:windowX";
    private static final String PROPNAME_WINDOW_Y = "android:changeBounds:windowY";

    private static final String[] TRANSITION_PROPERTIES = {
        PROPNAME_BOUNDS,
        PROPNAME_CLIP,
        PROPNAME_PARENT,
        PROPNAME_WINDOW_X,
        PROPNAME_WINDOW_Y,
    };

    private boolean resizeClip;
    private boolean reparent;

    public ChangeBounds() {}

    public ChangeBounds(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public String[] getTransitionProperties() {
        return TRANSITION_PROPERTIES.clone();
    }

    public void setResizeClip(boolean resizeClip) {
        this.resizeClip = resizeClip;
    }

    public boolean getResizeClip() {
        return resizeClip;
    }

    @Deprecated
    public void setReparent(boolean reparent) {
        this.reparent = reparent;
    }

    private void captureBoundsValues(TransitionValues values) {
        View view = values.view;
        if (view == null) {
            return;
        }
        values.values.put(
                PROPNAME_BOUNDS,
                new Rect(view.getLeft(), view.getTop(), view.getRight(), view.getBottom()));
        values.values.put(PROPNAME_PARENT, view.getParent());
        if (reparent) {
            int[] loc = new int[2];
            view.getLocationInWindow(loc);
            values.values.put(PROPNAME_WINDOW_X, Integer.valueOf(loc[0]));
            values.values.put(PROPNAME_WINDOW_Y, Integer.valueOf(loc[1]));
        }
        if (resizeClip) {
            values.values.put(PROPNAME_CLIP, view.getClipBounds());
        }
    }

    @Override
    public void captureStartValues(TransitionValues transitionValues) {
        captureBoundsValues(transitionValues);
    }

    @Override
    public void captureEndValues(TransitionValues transitionValues) {
        captureBoundsValues(transitionValues);
    }

    @Override
    public Animator createAnimator(
            ViewGroup sceneRoot, TransitionValues startValues, TransitionValues endValues) {
        if (startValues == null || endValues == null) {
            return null;
        }
        final View view = endValues.view;
        final Rect startBounds = (Rect) startValues.values.get(PROPNAME_BOUNDS);
        final Rect endBounds = (Rect) endValues.values.get(PROPNAME_BOUNDS);
        if (startBounds == null || endBounds == null) {
            return null;
        }
        final int startLeft = startBounds.left;
        final int endLeft = endBounds.left;
        final int startTop = startBounds.top;
        final int endTop = endBounds.top;
        final int startRight = startBounds.right;
        final int endRight = endBounds.right;
        final int startBottom = startBounds.bottom;
        final int endBottom = endBounds.bottom;
        Rect startClip = (Rect) startValues.values.get(PROPNAME_CLIP);
        final Rect endClip = (Rect) endValues.values.get(PROPNAME_CLIP);

        boolean boundsChanged =
                startLeft != endLeft
                        || startTop != endTop
                        || startRight != endRight
                        || startBottom != endBottom;
        boolean clipChanged =
                (startClip != null && !startClip.equals(endClip))
                        || (startClip == null && endClip != null);
        if (!boundsChanged && !clipChanged) {
            return null;
        }

        Path topLeftPath = getPathMotion().getPath(startLeft, startTop, endLeft, endTop);
        Path bottomRightPath = getPathMotion().getPath(startRight, startBottom, endRight, endBottom);
        final PathMeasure tlMeasure = new PathMeasure(topLeftPath, false);
        final PathMeasure brMeasure = new PathMeasure(bottomRightPath, false);
        final float tlLen = tlMeasure.getLength();
        final float brLen = brMeasure.getLength();
        final float[] pos = new float[2];

        ValueAnimator boundsAnim = ValueAnimator.ofFloat(0f, 1f);
        boundsAnim.addUpdateListener(
                new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        float f = ((Float) animation.getAnimatedValue()).floatValue();
                        int l;
                        int t;
                        int r;
                        int b;
                        if (tlLen > 0f && tlMeasure.getPosTan(tlLen * f, pos, null)) {
                            l = Math.round(pos[0]);
                            t = Math.round(pos[1]);
                        } else {
                            l = Math.round(startLeft + (endLeft - startLeft) * f);
                            t = Math.round(startTop + (endTop - startTop) * f);
                        }
                        if (brLen > 0f && brMeasure.getPosTan(brLen * f, pos, null)) {
                            r = Math.round(pos[0]);
                            b = Math.round(pos[1]);
                        } else {
                            r = Math.round(startRight + (endRight - startRight) * f);
                            b = Math.round(startBottom + (endBottom - startBottom) * f);
                        }
                        view.setLeft(l);
                        view.setTop(t);
                        view.setRight(r);
                        view.setBottom(b);
                    }
                });

        if (!resizeClip || !clipChanged) {
            return boundsAnim;
        }

        Rect fromClip =
                startClip != null
                        ? startClip
                        : new Rect(0, 0, startRight - startLeft, startBottom - startTop);
        Rect toClip =
                endClip != null
                        ? endClip
                        : new Rect(0, 0, endRight - endLeft, endBottom - endTop);
        ObjectAnimator clipAnim =
                ObjectAnimator.ofObject(view, "clipBounds", new RectEvaluator(new Rect()), fromClip, toClip);
        clipAnim.addListener(
                new AnimatorListenerAdapter() {
                    private boolean canceled;

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        canceled = true;
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (!canceled) {
                            view.setClipBounds(endClip);
                        }
                    }
                });
        AnimatorSet set = new AnimatorSet();
        set.playTogether(boundsAnim, clipAnim);
        return set;
    }
}
