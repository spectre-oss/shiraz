// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes.units;

public final class Energy implements Comparable<Energy> {
    private final double mInCalories;

    private Energy(double value) {
        mInCalories = value;
    }

    public static Energy fromCalories(double value) {
        return new Energy(value);
    }

    public double getInCalories() {
        return mInCalories;
    }

    @Override
    public int compareTo(Energy other) {
        return Double.compare(mInCalories, other.mInCalories);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof Energy)) return false;
        return Double.compare(mInCalories, ((Energy) object).mInCalories) == 0;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(mInCalories);
    }

    @Override
    public String toString() {
        return mInCalories + " cal";
    }
}
