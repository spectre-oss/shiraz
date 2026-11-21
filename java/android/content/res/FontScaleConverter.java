// SPDX-License-Identifier: GPL-3.0-only
package android.content.res;

import java.util.Arrays;

/**
 * Converts text dimensions between SP and DP using Android 14+ non-linear font scaling curves
 * so large text stays readable without clipping headlines.
 */
public interface FontScaleConverter {
    float convertSpToDp(float sp);

    float convertDpToSp(float dp);

    static boolean isNonLinearFontScalingActive(float fontScale) {
        return fontScale >= 1.03f;
    }

    static FontScaleConverter forScale(float fontScale) {
        if (!isNonLinearFontScalingActive(fontScale)) {
            return null;
        }
        return FontScaleConverterImpl.forScale(fontScale);
    }
}

final class FontScaleConverterImpl implements FontScaleConverter {
    private static final float[] COMMON_SP = {8f, 10f, 12f, 14f, 18f, 20f, 24f, 30f, 100f};
    private static final float[][] DP_TABLES = {
            // 1.05x
            {8.4f, 10.5f, 12.6f, 14.8f, 18.6f, 20.6f, 24.4f, 30.0f, 100f},
            // 1.15x
            {9.2f, 11.5f, 13.8f, 16.4f, 19.8f, 21.8f, 25.2f, 30.0f, 100f},
            // 1.30x
            {10.4f, 13.0f, 15.6f, 18.8f, 21.6f, 23.6f, 26.4f, 30.0f, 100f},
            // 1.50x
            {12.0f, 15.0f, 18.0f, 22.0f, 24.0f, 26.0f, 28.0f, 30.0f, 100f},
            // 1.80x
            {14.4f, 18.0f, 21.6f, 24.4f, 27.6f, 30.8f, 32.8f, 34.8f, 100f},
            // 2.00x
            {16.0f, 20.0f, 24.0f, 26.0f, 30.0f, 34.0f, 36.0f, 38.0f, 100f},
    };
    private static final float[] SCALES = {1.05f, 1.15f, 1.30f, 1.50f, 1.80f, 2.00f};

    private final float[] mFromSpValues;
    private final float[] mToDpValues;

    private FontScaleConverterImpl(float[] fromSp, float[] toDp) {
        mFromSpValues = fromSp;
        mToDpValues = toDp;
    }

    static FontScaleConverter forScale(float fontScale) {
        if (fontScale <= 1.0f) {
            return new FontScaleConverterImpl(new float[] {1f}, new float[] {fontScale});
        }
        int idx = Arrays.binarySearch(SCALES, fontScale);
        if (idx >= 0) {
            return new FontScaleConverterImpl(COMMON_SP, DP_TABLES[idx]);
        }
        int insert = -(idx + 1);
        if (insert == 0) {
            float t = (fontScale - 1.0f) / (SCALES[0] - 1.0f);
            float[] dp = new float[COMMON_SP.length];
            for (int i = 0; i < COMMON_SP.length; i++) {
                dp[i] = COMMON_SP[i] + t * (DP_TABLES[0][i] - COMMON_SP[i]);
            }
            return new FontScaleConverterImpl(COMMON_SP, dp);
        }
        if (insert >= SCALES.length) {
            return new FontScaleConverterImpl(COMMON_SP, DP_TABLES[SCALES.length - 1]);
        }
        float lowScale = SCALES[insert - 1];
        float highScale = SCALES[insert];
        float t = (fontScale - lowScale) / (highScale - lowScale);
        float[] lowDp = DP_TABLES[insert - 1];
        float[] highDp = DP_TABLES[insert];
        float[] dp = new float[COMMON_SP.length];
        for (int i = 0; i < COMMON_SP.length; i++) {
            dp[i] = lowDp[i] + t * (highDp[i] - lowDp[i]);
        }
        return new FontScaleConverterImpl(COMMON_SP, dp);
    }

    @Override
    public float convertSpToDp(float sp) {
        return lookupAndInterpolate(sp, mFromSpValues, mToDpValues);
    }

    @Override
    public float convertDpToSp(float dp) {
        return lookupAndInterpolate(dp, mToDpValues, mFromSpValues);
    }

    private static float lookupAndInterpolate(float sourceValue, float[] sourceValues, float[] targetValues) {
        float abs = Math.abs(sourceValue);
        float sign = Math.signum(sourceValue);
        int index = Arrays.binarySearch(sourceValues, abs);
        if (index >= 0) {
            return sign * targetValues[index];
        }
        int lowerIndex = -(index + 1) - 1;
        if (lowerIndex >= sourceValues.length - 1) {
            float startSp = sourceValues[sourceValues.length - 1];
            float startDp = targetValues[sourceValues.length - 1];
            if (startSp == 0f) return 0f;
            return sourceValue * (startDp / startSp);
        }
        if (lowerIndex == -1) {
            float endSp = sourceValues[0];
            float endDp = targetValues[0];
            if (endSp == 0f) return 0f;
            return sign * (abs / endSp) * endDp;
        }
        float startS = sourceValues[lowerIndex];
        float endS = sourceValues[lowerIndex + 1];
        float startT = targetValues[lowerIndex];
        float endT = targetValues[lowerIndex + 1];
        if (endS == startS) {
            return sign * startT;
        }
        float ratio = (abs - startS) / (endS - startS);
        return sign * (startT + ratio * (endT - startT));
    }
}
