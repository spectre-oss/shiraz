// SPDX-License-Identifier: GPL-3.0-only
package android.hardware.camera2.params;

import android.util.Rational;
import java.util.Arrays;
import java.util.Objects;

public final class ColorSpaceTransform {
    private static final int ROWS = 3;
    private static final int COLUMNS = 3;
    private static final int COUNT = ROWS * COLUMNS;
    private static final int COUNT_INT = COUNT * 2;

    private final int[] mElements;

    public ColorSpaceTransform(Rational[] elements) {
        Objects.requireNonNull(elements, "elements must not be null");
        if (elements.length != COUNT) {
            throw new IllegalArgumentException(
                    "elements must be " + COUNT + " length");
        }
        mElements = new int[COUNT_INT];
        for (int i = 0; i < COUNT; i++) {
            Rational el = Objects.requireNonNull(elements[i], "element[" + i + "] must not be null");
            mElements[i * 2] = el.getNumerator();
            mElements[i * 2 + 1] = el.getDenominator();
        }
    }

    public ColorSpaceTransform(int[] elements) {
        Objects.requireNonNull(elements, "elements must not be null");
        if (elements.length != COUNT_INT) {
            throw new IllegalArgumentException(
                    "elements must be " + COUNT_INT + " length");
        }
        mElements = new int[COUNT_INT];
        for (int i = 0; i < COUNT; i++) {
            Rational norm = new Rational(elements[i * 2], elements[i * 2 + 1]);
            mElements[i * 2] = norm.getNumerator();
            mElements[i * 2 + 1] = norm.getDenominator();
        }
    }

    public Rational getElement(int column, int row) {
        if (column < 0 || column >= COLUMNS) {
            throw new IllegalArgumentException("column out of range");
        }
        if (row < 0 || row >= ROWS) {
            throw new IllegalArgumentException("row out of range");
        }
        int idx = (row * COLUMNS + column) * 2;
        return new Rational(mElements[idx], mElements[idx + 1]);
    }

    public void copyElements(Rational[] destination, int offset) {
        Objects.requireNonNull(destination, "destination must not be null");
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
        if (destination.length - offset < COUNT) {
            throw new ArrayIndexOutOfBoundsException("destination too small to fit elements");
        }
        for (int i = 0; i < COUNT; i++) {
            destination[offset + i] = new Rational(mElements[i * 2], mElements[i * 2 + 1]);
        }
    }

    public void copyElements(int[] destination, int offset) {
        Objects.requireNonNull(destination, "destination must not be null");
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
        if (destination.length - offset < COUNT_INT) {
            throw new ArrayIndexOutOfBoundsException("destination too small to fit elements");
        }
        System.arraycopy(mElements, 0, destination, offset, COUNT_INT);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (this == obj) return true;
        if (obj instanceof ColorSpaceTransform) {
            return Arrays.equals(mElements, ((ColorSpaceTransform) obj).mElements);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(mElements);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ColorSpaceTransform(");
        for (int r = 0; r < ROWS; r++) {
            sb.append("[");
            for (int c = 0; c < COLUMNS; c++) {
                sb.append(getElement(c, r));
                if (c < COLUMNS - 1) sb.append(", ");
            }
            sb.append("]");
            if (r < ROWS - 1) sb.append(", ");
        }
        sb.append(")");
        return sb.toString();
    }
}
