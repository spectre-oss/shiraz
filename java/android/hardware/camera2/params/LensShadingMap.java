// SPDX-License-Identifier: GPL-3.0-only

package android.hardware.camera2.params;

import java.util.Arrays;

public final class LensShadingMap {
    public static final float MINIMUM_GAIN_FACTOR = 1.0f;

    private final int mRows;
    private final int mColumns;
    private final float[] mElements;

    public LensShadingMap(float[] elements, int rows, int columns) {
        if (rows <= 0) throw new IllegalArgumentException("rows must be positive");
        if (columns <= 0) throw new IllegalArgumentException("columns must be positive");
        if (elements == null) throw new NullPointerException("elements must not be null");
        mRows = rows;
        mColumns = columns;
        int expected = getGainFactorCount();
        if (elements.length != expected) {
            throw new IllegalArgumentException(
                    "elements length " + elements.length + " != expected " + expected);
        }
        mElements = new float[expected];
        for (int i = 0; i < expected; i++) {
            float v = elements[i];
            if (!Float.isFinite(v) || v < MINIMUM_GAIN_FACTOR) {
                throw new IllegalArgumentException("Gain factor out of range: " + v);
            }
            mElements[i] = v;
        }
    }

    public int getRowCount() {
        return mRows;
    }

    public int getColumnCount() {
        return mColumns;
    }

    public int getGainFactorCount() {
        return mRows * mColumns * RggbChannelVector.COUNT;
    }

    public float getGainFactor(int colorChannel, int column, int row) {
        if (colorChannel < 0 || colorChannel >= RggbChannelVector.COUNT) {
            throw new IllegalArgumentException("colorChannel out of range");
        }
        if (column < 0 || column >= mColumns) {
            throw new IllegalArgumentException("column out of range");
        }
        if (row < 0 || row >= mRows) {
            throw new IllegalArgumentException("row out of range");
        }
        return mElements[(row * mColumns + column) * RggbChannelVector.COUNT + colorChannel];
    }

    public RggbChannelVector getGainFactorVector(int column, int row) {
        if (column < 0 || column >= mColumns) {
            throw new IllegalArgumentException("column out of range");
        }
        if (row < 0 || row >= mRows) {
            throw new IllegalArgumentException("row out of range");
        }
        int base = (row * mColumns + column) * RggbChannelVector.COUNT;
        return new RggbChannelVector(
                mElements[base + RggbChannelVector.RED],
                mElements[base + RggbChannelVector.GREEN_EVEN],
                mElements[base + RggbChannelVector.GREEN_ODD],
                mElements[base + RggbChannelVector.BLUE]);
    }

    public void copyGainFactors(float[] destination, int offset) {
        if (destination == null) {
            throw new NullPointerException("destination must not be null");
        }
        if (offset < 0 || destination.length - offset < mElements.length) {
            throw new ArrayIndexOutOfBoundsException("destination too small for offset " + offset);
        }
        System.arraycopy(mElements, 0, destination, offset, mElements.length);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof LensShadingMap)) return false;
        LensShadingMap other = (LensShadingMap) obj;
        return mRows == other.mRows
                && mColumns == other.mColumns
                && Arrays.equals(mElements, other.mElements);
    }

    @Override
    public int hashCode() {
        int h = mRows;
        h = 31 * h + mColumns;
        h = 31 * h + Arrays.hashCode(mElements);
        return h;
    }

    @Override
    public String toString() {
        return "LensShadingMap{rows=" + mRows + ", cols=" + mColumns + "}";
    }
}
