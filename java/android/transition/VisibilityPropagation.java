// SPDX-License-Identifier: GPL-3.0-only
package android.transition;

import android.view.View;

/** Captures view visibility and screen center coordinates for spatial transition propagation. */
public abstract class VisibilityPropagation extends TransitionPropagation {
    private static final String PROPNAME_VISIBILITY = "android:visibilityPropagation:visibility";
    private static final String PROPNAME_VIEW_CENTER = "android:visibilityPropagation:center";
    private static final String[] VISIBILITY_PROPAGATION_VALUES = {
        PROPNAME_VISIBILITY,
        PROPNAME_VIEW_CENTER,
    };

    public VisibilityPropagation() {}

    @Override
    public void captureValues(TransitionValues values) {
        if (values == null || values.view == null) {
            return;
        }
        View view = values.view;
        Integer visibility = (Integer) values.values.get("android:visibility:visibility");
        if (visibility == null) {
            visibility = Integer.valueOf(view.getVisibility());
        }
        values.values.put(PROPNAME_VISIBILITY, visibility);
        int[] loc = new int[2];
        view.getLocationOnScreen(loc);
        loc[0] += Math.round(view.getTranslationX()) + view.getWidth() / 2;
        loc[1] += Math.round(view.getTranslationY()) + view.getHeight() / 2;
        values.values.put(PROPNAME_VIEW_CENTER, loc);
    }

    @Override
    public String[] getPropagationProperties() {
        return VISIBILITY_PROPAGATION_VALUES.clone();
    }

    public int getViewVisibility(TransitionValues values) {
        if (values == null) {
            return View.GONE;
        }
        Integer visibility = (Integer) values.values.get(PROPNAME_VISIBILITY);
        return visibility == null ? View.GONE : visibility.intValue();
    }

    public int getViewX(TransitionValues values) {
        return getViewCoordinate(values, 0);
    }

    public int getViewY(TransitionValues values) {
        return getViewCoordinate(values, 1);
    }

    private static int getViewCoordinate(TransitionValues values, int coordinateIndex) {
        if (values == null) {
            return -1;
        }
        int[] coordinates = (int[]) values.values.get(PROPNAME_VIEW_CENTER);
        if (coordinates == null) {
            return -1;
        }
        return coordinates[coordinateIndex];
    }
}
