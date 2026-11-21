// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes.units;

public final class Velocity implements Comparable<Velocity> {
    private final double mInMetersPerSecond;

    private Velocity(double value) {
        mInMetersPerSecond = value;
    }

    public static Velocity fromMetersPerSecond(double value) {
        return new Velocity(value);
    }

    public double getInMetersPerSecond() {
        return mInMetersPerSecond;
    }

    @Override
    public int compareTo(Velocity other) {
        return Double.compare(mInMetersPerSecond, other.mInMetersPerSecond);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof Velocity)) return false;
        return Double.compare(mInMetersPerSecond, ((Velocity) object).mInMetersPerSecond) == 0;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(mInMetersPerSecond);
    }

    @Override
    public String toString() {
        return mInMetersPerSecond + " m/s";
    }
}
