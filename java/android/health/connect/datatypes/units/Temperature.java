// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes.units;

public final class Temperature implements Comparable<Temperature> {
    private final double mInCelsius;

    private Temperature(double value) {
        mInCelsius = value;
    }

    public static Temperature fromCelsius(double value) {
        return new Temperature(value);
    }

    public double getInCelsius() {
        return mInCelsius;
    }

    @Override
    public int compareTo(Temperature other) {
        return Double.compare(mInCelsius, other.mInCelsius);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof Temperature)) return false;
        return Double.compare(mInCelsius, ((Temperature) object).mInCelsius) == 0;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(mInCelsius);
    }

    @Override
    public String toString() {
        return mInCelsius + " C";
    }
}
