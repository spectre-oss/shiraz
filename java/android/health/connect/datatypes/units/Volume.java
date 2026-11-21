// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes.units;

public final class Volume implements Comparable<Volume> {
    private final double mInLiters;

    private Volume(double value) {
        mInLiters = value;
    }

    public static Volume fromLiters(double value) {
        return new Volume(value);
    }

    public double getInLiters() {
        return mInLiters;
    }

    @Override
    public int compareTo(Volume other) {
        return Double.compare(mInLiters, other.mInLiters);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof Volume)) return false;
        return Double.compare(mInLiters, ((Volume) object).mInLiters) == 0;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(mInLiters);
    }

    @Override
    public String toString() {
        return mInLiters + " L";
    }
}
