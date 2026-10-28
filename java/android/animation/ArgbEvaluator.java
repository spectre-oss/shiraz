// SPDX-License-Identifier: GPL-3.0-only
package android.animation;

/** Interpolates ARGB colors with linear alpha and gamma-corrected RGB channels. */
public class ArgbEvaluator implements TypeEvaluator<Object> {
    private static final ArgbEvaluator INSTANCE = new ArgbEvaluator();
    public static ArgbEvaluator getInstance() { return INSTANCE; }
    @Override public Object evaluate(float fraction, Object startValue, Object endValue) {
        int start = (Integer) startValue, end = (Integer) endValue;
        int alpha = Math.round((start >>> 24) + fraction * ((end >>> 24) - (start >>> 24)));
        int color = alpha << 24;
        for (int shift = 16; shift >= 0; shift -= 8) {
            double a = Math.pow(((start >>> shift) & 255) / 255.0, 2.2);
            double b = Math.pow(((end >>> shift) & 255) / 255.0, 2.2);
            int channel = (int) Math.round(Math.pow(a + fraction * (b - a), 1.0 / 2.2) * 255.0);
            color |= channel << shift;
        }
        return color;
    }
}
