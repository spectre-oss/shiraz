// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;

public class SumPathEffect extends PathEffect {
    private final PathEffect mFirst;
    private final PathEffect mSecond;

    public SumPathEffect(PathEffect first, PathEffect second) {
        if (first == null || second == null) {
            throw new NullPointerException("PathEffect arguments must not be null");
        }
        shirazValidate(first);
        shirazValidate(second);
        mFirst = first;
        mSecond = second;
    }

    Path apply(Path source) {
        Path out = new Path(shirazApply(mFirst, source));
        out.addPath(shirazApply(mSecond, source));
        return out;
    }
}
