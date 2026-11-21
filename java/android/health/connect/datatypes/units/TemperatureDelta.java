// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes.units;

public final class TemperatureDelta implements Comparable<TemperatureDelta> {
    private final double mInCelsius;

    private TemperatureDelta(double value) {
        mInCelsius = value;
    }

    public static TemperatureDelta fromCelsius(double value) {
        return new TemperatureDelta(value);
    }

    public double getInCelsius() {
        return mInCelsius;
    }

    @Override
    public int compareTo(TemperatureDelta other) {
        return Double.compare(mInCelsius, other.mInCelsius);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof TemperatureDelta)) return false;
        return Double.compare(mInCelsius, ((TemperatureDelta) object).mInCelsius) == 0;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(mInCelsius);
    }

    @Override
    public String toString() {
        return mInCelsius + " dC";
    }
}
