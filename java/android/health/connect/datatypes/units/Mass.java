// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes.units;

public final class Mass implements Comparable<Mass> {
    private final double mInGrams;

    private Mass(double value) {
        mInGrams = value;
    }

    public static Mass fromGrams(double value) {
        return new Mass(value);
    }

    public double getInGrams() {
        return mInGrams;
    }

    @Override
    public int compareTo(Mass other) {
        return Double.compare(mInGrams, other.mInGrams);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof Mass)) return false;
        return Double.compare(mInGrams, ((Mass) object).mInGrams) == 0;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(mInGrams);
    }

    @Override
    public String toString() {
        return mInGrams + " g";
    }
}
