// SPDX-License-Identifier: GPL-3.0-only
package android.animation;

import android.graphics.PointF;

/** Linearly interpolates between two {@link PointF} coordinates. */
public class PointFEvaluator implements TypeEvaluator<PointF> {
    private final PointF point;

    public PointFEvaluator() {
        this(null);
    }

    public PointFEvaluator(PointF reuse) {
        this.point = reuse;
    }

    @Override
    public PointF evaluate(float fraction, PointF startValue, PointF endValue) {
        float x = startValue.x + fraction * (endValue.x - startValue.x);
        float y = startValue.y + fraction * (endValue.y - startValue.y);
        if (point != null) {
            point.set(x, y);
            return point;
        }
        return new PointF(x, y);
    }
}
