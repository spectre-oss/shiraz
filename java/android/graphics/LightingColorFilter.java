// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;

public class LightingColorFilter extends ColorFilter {
    private final int mMul;
    private final int mAdd;
    private final float[] mMatrix;

    public LightingColorFilter(int mul, int add) {
        mMul = mul;
        mAdd = add;
        float mr = ((mul >>> 16) & 0xFF) / 255.0f;
        float mg = ((mul >>> 8) & 0xFF) / 255.0f;
        float mb = (mul & 0xFF) / 255.0f;
        float ar = (add >>> 16) & 0xFF;
        float ag = (add >>> 8) & 0xFF;
        float ab = add & 0xFF;
        mMatrix = new float[] {
            mr, 0f, 0f, 0f, ar,
            0f, mg, 0f, 0f, ag,
            0f, 0f, mb, 0f, ab,
            0f, 0f, 0f, 1f, 0f
        };
    }

    public int getColorMultiply() {
        return mMul;
    }

    public int getColorAdd() {
        return mAdd;
    }

    float[] shirazMatrix() {
        return mMatrix;
    }
}
