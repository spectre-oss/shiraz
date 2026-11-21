// SPDX-License-Identifier: GPL-3.0-only
package android.transition;

import android.animation.Animator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/** Base {@link Transition} animating views appearing into or disappearing from a scene. */
public abstract class Visibility extends Transition {
    static final String PROPNAME_VISIBILITY = "android:visibility:visibility";
    private static final String PROPNAME_PARENT = "android:visibility:parent";
    private static final String PROPNAME_SCREEN_LOCATION = "android:visibility:screenLocation";

    public static final int MODE_IN = 0x1;
    public static final int MODE_OUT = 0x2;

    private static final String[] TRANSITION_PROPERTIES = {
        PROPNAME_VISIBILITY,
        PROPNAME_PARENT,
    };

    private int mode = MODE_IN | MODE_OUT;

    public Visibility() {}

    public Visibility(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setMode(int mode) {
        if ((mode & ~(MODE_IN | MODE_OUT)) != 0) {
            throw new IllegalArgumentException("Only MODE_IN and MODE_OUT flags are allowed");
        }
        this.mode = mode;
    }

    public int getMode() {
        return mode;
    }

    @Override
    public String[] getTransitionProperties() {
        return TRANSITION_PROPERTIES.clone();
    }

    private void captureVisibilityValues(TransitionValues transitionValues) {
        int visibility = transitionValues.view.getVisibility();
        transitionValues.values.put(PROPNAME_VISIBILITY, Integer.valueOf(visibility));
        transitionValues.values.put(PROPNAME_PARENT, transitionValues.view.getParent());
        int[] loc = new int[2];
        transitionValues.view.getLocationOnScreen(loc);
        transitionValues.values.put(PROPNAME_SCREEN_LOCATION, loc);
    }

    @Override
    public void captureStartValues(TransitionValues transitionValues) {
        captureVisibilityValues(transitionValues);
    }

    @Override
    public void captureEndValues(TransitionValues transitionValues) {
        captureVisibilityValues(transitionValues);
    }

    public boolean isVisible(TransitionValues values) {
        if (values == null) {
            return false;
        }
        Integer visObj = (Integer) values.values.get(PROPNAME_VISIBILITY);
        int visibility = visObj == null ? View.GONE : visObj.intValue();
        View parent = (View) values.values.get(PROPNAME_PARENT);
        return visibility == View.VISIBLE && parent != null;
    }

    private static final class VisibilityInfo {
        boolean visibilityChange;
        boolean fadeIn;
        int startVisibility;
        int endVisibility;
        ViewGroup startParent;
        ViewGroup endParent;
    }

    private static VisibilityInfo getVisibilityChangeInfo(
            TransitionValues startValues, TransitionValues endValues) {
        VisibilityInfo visInfo = new VisibilityInfo();
        visInfo.visibilityChange = false;
        visInfo.fadeIn = false;
        if (startValues != null && startValues.values.containsKey(PROPNAME_VISIBILITY)) {
            visInfo.startVisibility = ((Integer) startValues.values.get(PROPNAME_VISIBILITY)).intValue();
            visInfo.startParent = (ViewGroup) startValues.values.get(PROPNAME_PARENT);
        } else {
            visInfo.startVisibility = -1;
            visInfo.startParent = null;
        }
        if (endValues != null && endValues.values.containsKey(PROPNAME_VISIBILITY)) {
            visInfo.endVisibility = ((Integer) endValues.values.get(PROPNAME_VISIBILITY)).intValue();
            visInfo.endParent = (ViewGroup) endValues.values.get(PROPNAME_PARENT);
        } else {
            visInfo.endVisibility = -1;
            visInfo.endParent = null;
        }
        if (startValues != null && endValues != null) {
            if (visInfo.startVisibility == visInfo.endVisibility
                    && visInfo.startParent == visInfo.endParent) {
                return visInfo;
            }
            if (visInfo.startVisibility != visInfo.endVisibility) {
                if (visInfo.startVisibility == View.VISIBLE) {
                    visInfo.fadeIn = false;
                    visInfo.visibilityChange = true;
                } else if (visInfo.endVisibility == View.VISIBLE) {
                    visInfo.fadeIn = true;
                    visInfo.visibilityChange = true;
                }
            } else if (visInfo.endParent == null) {
                visInfo.fadeIn = false;
                visInfo.visibilityChange = true;
            } else if (visInfo.startParent == null) {
                visInfo.fadeIn = true;
                visInfo.visibilityChange = true;
            }
        } else if (startValues == null && visInfo.endVisibility == View.VISIBLE) {
            visInfo.fadeIn = true;
            visInfo.visibilityChange = true;
        } else if (endValues == null && visInfo.startVisibility == View.VISIBLE) {
            visInfo.fadeIn = false;
            visInfo.visibilityChange = true;
        }
        return visInfo;
    }

    @Override
    public Animator createAnimator(
            ViewGroup sceneRoot, TransitionValues startValues, TransitionValues endValues) {
        VisibilityInfo visInfo = getVisibilityChangeInfo(startValues, endValues);
        if (visInfo.visibilityChange
                && (visInfo.startParent != null || visInfo.endParent != null)) {
            if (visInfo.fadeIn) {
                return onAppear(
                        sceneRoot, startValues, visInfo.startVisibility, endValues, visInfo.endVisibility);
            }
            return onDisappear(
                    sceneRoot, startValues, visInfo.startVisibility, endValues, visInfo.endVisibility);
        }
        return null;
    }

    public Animator onAppear(
            ViewGroup sceneRoot,
            TransitionValues startValues,
            int startVisibility,
            TransitionValues endValues,
            int endVisibility) {
        if ((mode & MODE_IN) != MODE_IN || endValues == null) {
            return null;
        }
        if (startValues == null && endValues.view.getParent() instanceof View) {
            View endParent = (View) endValues.view.getParent();
            TransitionValues startParentValues = getMatchedTransitionValues(endParent, false);
            TransitionValues endParentValues = getTransitionValues(endParent, false);
            VisibilityInfo parentVisibilityInfo = getVisibilityChangeInfo(startParentValues, endParentValues);
            if (parentVisibilityInfo.visibilityChange) {
                return null;
            }
        }
        return onAppear(sceneRoot, endValues.view, startValues, endValues);
    }

    public Animator onAppear(
            ViewGroup sceneRoot,
            View view,
            TransitionValues startValues,
            TransitionValues endValues) {
        return null;
    }

    public Animator onDisappear(
            ViewGroup sceneRoot,
            TransitionValues startValues,
            int startVisibility,
            TransitionValues endValues,
            int endVisibility) {
        if ((mode & MODE_OUT) != MODE_OUT) {
            return null;
        }
        View startView = startValues != null ? startValues.view : null;
        View endView = endValues != null ? endValues.view : null;
        View targetView = endView != null ? endView : startView;
        if (targetView == null) {
            return null;
        }
        return onDisappear(sceneRoot, targetView, startValues, endValues);
    }

    public Animator onDisappear(
            ViewGroup sceneRoot,
            View view,
            TransitionValues startValues,
            TransitionValues endValues) {
        return null;
    }

    @Override
    public boolean isTransitionRequired(TransitionValues startValues, TransitionValues newValues) {
        if (startValues == null && newValues == null) {
            return false;
        }
        if (startValues != null
                && newValues != null
                && newValues.values.containsKey(PROPNAME_VISIBILITY)
                        != startValues.values.containsKey(PROPNAME_VISIBILITY)) {
            return false;
        }
        VisibilityInfo changeInfo = getVisibilityChangeInfo(startValues, newValues);
        return changeInfo.visibilityChange
                && (changeInfo.startVisibility == View.VISIBLE
                        || changeInfo.endVisibility == View.VISIBLE);
    }
}
