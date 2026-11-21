// SPDX-License-Identifier: GPL-3.0-only
package android.animation;

import android.graphics.Rect;

/** Linearly interpolates between two {@link Rect} bounds. */
public class RectEvaluator implements TypeEvaluator<Rect> {
    private final Rect rect;

    public RectEvaluator() {
        this(null);
    }

    public RectEvaluator(Rect reuseRect) {
        this.rect = reuseRect;
    }

    @Override
    public Rect evaluate(float fraction, Rect startValue, Rect endValue) {
        int left = startValue.left + (int) ((endValue.left - startValue.left) * fraction);
        int top = startValue.top + (int) ((endValue.top - startValue.top) * fraction);
        int right = startValue.right + (int) ((endValue.right - startValue.right) * fraction);
        int bottom = startValue.bottom + (int) ((endValue.bottom - startValue.bottom) * fraction);
        if (rect != null) {
            rect.set(left, top, right, bottom);
            return rect;
        }
        return new Rect(left, top, right, bottom);
    }
}
