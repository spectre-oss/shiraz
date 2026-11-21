// SPDX-License-Identifier: GPL-3.0-only
package android.animation;

/** Linearly interpolates element-by-element between {@code float[]} values. */
public class FloatArrayEvaluator implements TypeEvaluator<float[]> {
    private final float[] array;

    public FloatArrayEvaluator() {
        this(null);
    }

    public FloatArrayEvaluator(float[] reuseArray) {
        this.array = reuseArray;
    }

    @Override
    public float[] evaluate(float fraction, float[] startValue, float[] endValue) {
        float[] out = array != null ? array : new float[startValue.length];
        for (int i = 0; i < out.length; i++) {
            float start = startValue[i];
            out[i] = start + fraction * (endValue[i] - start);
        }
        return out;
    }
}
