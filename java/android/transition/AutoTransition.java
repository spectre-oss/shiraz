// SPDX-License-Identifier: GPL-3.0-only
package android.transition;

import android.content.Context;
import android.util.AttributeSet;

/** Default sequential {@link TransitionSet}: {@link Fade#OUT}, {@link ChangeBounds}, {@link Fade#IN}. */
public class AutoTransition extends TransitionSet {
    public AutoTransition() {
        init();
    }

    public AutoTransition(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setOrdering(ORDERING_SEQUENTIAL);
        addTransition(new Fade(Fade.OUT))
                .addTransition(new ChangeBounds())
                .addTransition(new Fade(Fade.IN));
    }
}
