// SPDX-License-Identifier: GPL-3.0-only
package android.transition;

import android.view.ViewGroup;

/** Strategy calculating per-target start delays for a {@link Transition}. */
public abstract class TransitionPropagation {
    public TransitionPropagation() {}

    public abstract long getStartDelay(
            ViewGroup sceneRoot,
            Transition transition,
            TransitionValues startValues,
            TransitionValues endValues);

    public abstract void captureValues(TransitionValues transitionValues);

    public abstract String[] getPropagationProperties();
}
