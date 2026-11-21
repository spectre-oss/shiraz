// SPDX-License-Identifier: GPL-3.0-only
package android.transition;

import android.view.View;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** Captured view property map for start and end states of a {@link Transition}. */
public class TransitionValues {
    public View view;
    public final Map<String, Object> values = new HashMap<String, Object>();
    final ArrayList<Transition> targetedTransitions = new ArrayList<Transition>();

    @Deprecated
    public TransitionValues() {}

    public TransitionValues(View view) {
        this.view = view;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof TransitionValues) {
            TransitionValues that = (TransitionValues) other;
            return view == that.view && values.equals(that.values);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return 31 * (view != null ? view.hashCode() : 0) + values.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("TransitionValues@").append(Integer.toHexString(hashCode())).append(":\n");
        sb.append("    view = ").append(view).append("\n");
        sb.append("    values:");
        for (String name : values.keySet()) {
            sb.append("    ").append(name).append(": ").append(values.get(name)).append("\n");
        }
        return sb.toString();
    }
}
