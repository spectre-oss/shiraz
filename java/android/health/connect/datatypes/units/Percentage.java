// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes.units;

public final class Percentage implements Comparable<Percentage> {
    private final double mValue;

    private Percentage(double value) {
        mValue = value;
    }

    public static Percentage fromValue(double value) {
        return new Percentage(value);
    }

    public double getValue() {
        return mValue;
    }

    @Override
    public int compareTo(Percentage other) {
        return Double.compare(mValue, other.mValue);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof Percentage)) return false;
        return Double.compare(mValue, ((Percentage) object).mValue) == 0;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(mValue);
    }

    @Override
    public String toString() {
        return mValue + "%";
    }
}
