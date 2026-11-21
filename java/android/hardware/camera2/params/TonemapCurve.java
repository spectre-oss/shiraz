// SPDX-License-Identifier: GPL-3.0-only

package android.hardware.camera2.params;

import android.graphics.PointF;
import java.util.Arrays;

public final class TonemapCurve {
    public static final int CHANNEL_RED = 0;
    public static final int CHANNEL_GREEN = 1;
    public static final int CHANNEL_BLUE = 2;
    public static final float LEVEL_BLACK = 0.0f;
    public static final float LEVEL_WHITE = 1.0f;
    public static final int POINT_SIZE = 2;

    private final float[] mRed;
    private final float[] mGreen;
    private final float[] mBlue;

    public TonemapCurve(float[] red, float[] green, float[] blue) {
        mRed = validateCurve(red, "red");
        mGreen = validateCurve(green, "green");
        mBlue = validateCurve(blue, "blue");
    }

    private static float[] validateCurve(float[] curve, String name) {
        if (curve == null) {
            throw new NullPointerException(name + " must not be null");
        }
        if (curve.length < 2 * POINT_SIZE || (curve.length % POINT_SIZE) != 0) {
            throw new IllegalArgumentException(name + " must have at least 2 points (even length >= 4)");
        }
        float[] copy = new float[curve.length];
        for (int i = 0; i < curve.length; i++) {
            float v = curve[i];
            if (!Float.isFinite(v) || v < LEVEL_BLACK || v > LEVEL_WHITE) {
                throw new IllegalArgumentException(name + "[" + i + "] out of [0.0, 1.0]: " + v);
            }
            copy[i] = v;
        }
        return copy;
    }

    private float[] channelArray(int colorChannel) {
        switch (colorChannel) {
            case CHANNEL_RED:
                return mRed;
            case CHANNEL_GREEN:
                return mGreen;
            case CHANNEL_BLUE:
                return mBlue;
            default:
                throw new IllegalArgumentException("colorChannel out of range: " + colorChannel);
        }
    }

    public int getPointCount(int colorChannel) {
        return channelArray(colorChannel).length / POINT_SIZE;
    }

    public PointF getPoint(int colorChannel, int index) {
        float[] arr = channelArray(colorChannel);
        int count = arr.length / POINT_SIZE;
        if (index < 0 || index >= count) {
            throw new IllegalArgumentException("index out of range: " + index);
        }
        return new PointF(arr[index * POINT_SIZE], arr[index * POINT_SIZE + 1]);
    }

    public void copyColorCurve(int colorChannel, float[] destination, int offset) {
        float[] arr = channelArray(colorChannel);
        if (destination == null) {
            throw new NullPointerException("destination must not be null");
        }
        if (offset < 0 || destination.length - offset < arr.length) {
            throw new ArrayIndexOutOfBoundsException("destination too small for offset " + offset);
        }
        System.arraycopy(arr, 0, destination, offset, arr.length);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof TonemapCurve)) return false;
        TonemapCurve other = (TonemapCurve) obj;
        return Arrays.equals(mRed, other.mRed)
                && Arrays.equals(mGreen, other.mGreen)
                && Arrays.equals(mBlue, other.mBlue);
    }

    @Override
    public int hashCode() {
        int h = Arrays.hashCode(mRed);
        h = 31 * h + Arrays.hashCode(mGreen);
        h = 31 * h + Arrays.hashCode(mBlue);
        return h;
    }

    @Override
    public String toString() {
        return "TonemapCurve{R:" + Arrays.toString(mRed)
                + ", G:" + Arrays.toString(mGreen)
                + ", B:" + Arrays.toString(mBlue) + "}";
    }
}
