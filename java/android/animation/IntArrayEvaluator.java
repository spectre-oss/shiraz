// SPDX-License-Identifier: GPL-3.0-only
package android.animation;

/** Linearly interpolates element-by-element between {@code int[]} values. */
public class IntArrayEvaluator implements TypeEvaluator<int[]> {
    private final int[] array;

    public IntArrayEvaluator() {
        this(null);
    }

    public IntArrayEvaluator(int[] reuseArray) {
        this.array = reuseArray;
    }

    @Override
    public int[] evaluate(float fraction, int[] startValue, int[] endValue) {
        int[] out = array != null ? array : new int[startValue.length];
        for (int i = 0; i < out.length; i++) {
            int start = startValue[i];
            out[i] = (int) (start + fraction * (endValue[i] - start));
        }
        return out;
    }
}
