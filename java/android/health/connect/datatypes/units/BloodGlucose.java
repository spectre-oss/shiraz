// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes.units;

public final class BloodGlucose implements Comparable<BloodGlucose> {
    private final double mInMillimolesPerLiter;

    private BloodGlucose(double value) {
        mInMillimolesPerLiter = value;
    }

    public static BloodGlucose fromMillimolesPerLiter(double value) {
        return new BloodGlucose(value);
    }

    public double getInMillimolesPerLiter() {
        return mInMillimolesPerLiter;
    }

    @Override
    public int compareTo(BloodGlucose other) {
        return Double.compare(mInMillimolesPerLiter, other.mInMillimolesPerLiter);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof BloodGlucose)) return false;
        return Double.compare(mInMillimolesPerLiter, ((BloodGlucose) object).mInMillimolesPerLiter) == 0;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(mInMillimolesPerLiter);
    }

    @Override
    public String toString() {
        return mInMillimolesPerLiter + " mmol/L";
    }
}
