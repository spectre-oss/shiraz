// SPDX-License-Identifier: GPL-3.0-only

package android.hardware.camera2.params;

public final class RggbChannelVector {
    public static final int RED = 0;
    public static final int GREEN_EVEN = 1;
    public static final int GREEN_ODD = 2;
    public static final int BLUE = 3;
    public static final int COUNT = 4;

    private final float mRed;
    private final float mGreenEven;
    private final float mGreenOdd;
    private final float mBlue;

    public RggbChannelVector(float red, float greenEven, float greenOdd, float blue) {
        if (!Float.isFinite(red)) {
            throw new IllegalArgumentException("red must be finite");
        }
        if (!Float.isFinite(greenEven)) {
            throw new IllegalArgumentException("greenEven must be finite");
        }
        if (!Float.isFinite(greenOdd)) {
            throw new IllegalArgumentException("greenOdd must be finite");
        }
        if (!Float.isFinite(blue)) {
            throw new IllegalArgumentException("blue must be finite");
        }
        mRed = red;
        mGreenEven = greenEven;
        mGreenOdd = greenOdd;
        mBlue = blue;
    }

    public float getRed() {
        return mRed;
    }

    public float getGreenEven() {
        return mGreenEven;
    }

    public float getGreenOdd() {
        return mGreenOdd;
    }

    public float getBlue() {
        return mBlue;
    }

    public float getComponent(int colorChannel) {
        switch (colorChannel) {
            case RED:
                return mRed;
            case GREEN_EVEN:
                return mGreenEven;
            case GREEN_ODD:
                return mGreenOdd;
            case BLUE:
                return mBlue;
            default:
                throw new IllegalArgumentException("Invalid colorChannel: " + colorChannel);
        }
    }

    public void copyTo(float[] destination, int offset) {
        if (destination == null) {
            throw new NullPointerException("destination must not be null");
        }
        if (offset < 0 || destination.length - offset < COUNT) {
            throw new ArrayIndexOutOfBoundsException("destination too small for offset " + offset);
        }
        destination[offset + RED] = mRed;
        destination[offset + GREEN_EVEN] = mGreenEven;
        destination[offset + GREEN_ODD] = mGreenOdd;
        destination[offset + BLUE] = mBlue;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof RggbChannelVector)) return false;
        RggbChannelVector other = (RggbChannelVector) obj;
        return mRed == other.mRed
                && mGreenEven == other.mGreenEven
                && mGreenOdd == other.mGreenOdd
                && mBlue == other.mBlue;
    }

    @Override
    public int hashCode() {
        int h = Float.floatToIntBits(mRed);
        h = 31 * h + Float.floatToIntBits(mGreenEven);
        h = 31 * h + Float.floatToIntBits(mGreenOdd);
        h = 31 * h + Float.floatToIntBits(mBlue);
        return h;
    }

    @Override
    public String toString() {
        return String.format("RggbChannelVector{R:%f, G_even:%f, G_odd:%f, B:%f}",
                mRed, mGreenEven, mGreenOdd, mBlue);
    }
}
