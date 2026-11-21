// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;

@Deprecated
public class EmbossMaskFilter extends MaskFilter {
    final float[] mDirection;
    final float mAmbient;
    final float mSpecular;
    final float mBlurRadius;

    @Deprecated
    public EmbossMaskFilter(float[] direction, float ambient, float specular, float blurRadius) {
        if (direction == null) {
            throw new NullPointerException("direction must not be null");
        }
        if (direction.length < 3) {
            throw new ArrayIndexOutOfBoundsException("direction must have at least 3 elements");
        }
        mDirection = new float[] { direction[0], direction[1], direction[2] };
        mAmbient = ambient;
        mSpecular = specular;
        mBlurRadius = blurRadius;
    }
}
