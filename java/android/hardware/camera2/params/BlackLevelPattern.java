// SPDX-License-Identifier: GPL-3.0-only
package android.hardware.camera2.params;

import java.util.Arrays;
import java.util.Objects;

public final class BlackLevelPattern {
    public static final int COUNT = 4;

    private final int[] mCfaOffsets;

    public BlackLevelPattern(int[] offsets) {
        Objects.requireNonNull(offsets, "Null offsets array passed to constructor");
        if (offsets.length < COUNT) {
            throw new IllegalArgumentException("Invalid offsets array length");
        }
        mCfaOffsets = Arrays.copyOf(offsets, COUNT);
    }

    public int getOffsetForIndex(int column, int row) {
        if (row < 0 || column < 0) {
            throw new IllegalArgumentException("column, row arguments must be positive");
        }
        return mCfaOffsets[((row & 1) << 1) | (column & 1)];
    }

    public void copyTo(int[] destination, int offset) {
        Objects.requireNonNull(destination, "destination must not be null");
        if (offset < 0) {
            throw new IllegalArgumentException("Null offset passed to copyTo");
        }
        if (destination.length - offset < COUNT) {
            throw new ArrayIndexOutOfBoundsException("Destination too small to fit elements");
        }
        System.arraycopy(mCfaOffsets, 0, destination, offset, COUNT);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (this == obj) return true;
        if (obj instanceof BlackLevelPattern) {
            return Arrays.equals(((BlackLevelPattern) obj).mCfaOffsets, mCfaOffsets);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(mCfaOffsets);
    }

    @Override
    public String toString() {
        return String.format(
                "BlackLevelPattern([%d, %d], [%d, %d])",
                Integer.valueOf(mCfaOffsets[0]),
                Integer.valueOf(mCfaOffsets[1]),
                Integer.valueOf(mCfaOffsets[2]),
                Integer.valueOf(mCfaOffsets[3]));
    }
}
