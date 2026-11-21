// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;

public class BlurMaskFilter extends MaskFilter {
    public enum Blur {
        NORMAL,
        SOLID,
        OUTER,
        INNER
    }

    final float mRadius;
    final Blur mStyle;

    public BlurMaskFilter(float radius, Blur style) {
        if (style == null) {
            throw new NullPointerException("style must not be null");
        }
        if (!Float.isFinite(radius) || radius <= 0.0f) {
            throw new IllegalArgumentException("radius must be > 0");
        }
        mRadius = radius;
        mStyle = style;
    }
}
