// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes.units;

public final class Length implements Comparable<Length> {
    private final double mInMeters;

    private Length(double value) {
        mInMeters = value;
    }

    public static Length fromMeters(double value) {
        return new Length(value);
    }

    public double getInMeters() {
        return mInMeters;
    }

    @Override
    public int compareTo(Length other) {
        return Double.compare(mInMeters, other.mInMeters);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof Length)) return false;
        return Double.compare(mInMeters, ((Length) object).mInMeters) == 0;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(mInMeters);
    }

    @Override
    public String toString() {
        return mInMeters + " m";
    }
}
