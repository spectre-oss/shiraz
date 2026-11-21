// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes.units;

public final class Pressure implements Comparable<Pressure> {
    private final double mInMillimetersOfMercury;

    private Pressure(double value) {
        mInMillimetersOfMercury = value;
    }

    public static Pressure fromMillimetersOfMercury(double value) {
        return new Pressure(value);
    }

    public double getInMillimetersOfMercury() {
        return mInMillimetersOfMercury;
    }

    @Override
    public int compareTo(Pressure other) {
        return Double.compare(mInMillimetersOfMercury, other.mInMillimetersOfMercury);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof Pressure)) return false;
        return Double.compare(
                mInMillimetersOfMercury, ((Pressure) object).mInMillimetersOfMercury) == 0;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(mInMillimetersOfMercury);
    }

    @Override
    public String toString() {
        return mInMillimetersOfMercury + " mmHg";
    }
}
