// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;

public class ComposePathEffect extends PathEffect {
    private final PathEffect mOuter;
    private final PathEffect mInner;

    public ComposePathEffect(PathEffect outerpe, PathEffect innerpe) {
        if (outerpe == null || innerpe == null) {
            throw new NullPointerException("PathEffect arguments must not be null");
        }
        shirazValidate(outerpe);
        shirazValidate(innerpe);
        mOuter = outerpe;
        mInner = innerpe;
    }

    Path apply(Path source) {
        return shirazApply(mOuter, shirazApply(mInner, source));
    }
}
