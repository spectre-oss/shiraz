// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes.units;

public final class Power implements Comparable<Power> {
    private final double mInWatts;

    private Power(double value) {
        mInWatts = value;
    }

    public static Power fromWatts(double value) {
        return new Power(value);
    }

    public double getInWatts() {
        return mInWatts;
    }

    @Override
    public int compareTo(Power other) {
        return Double.compare(mInWatts, other.mInWatts);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof Power)) return false;
        return Double.compare(mInWatts, ((Power) object).mInWatts) == 0;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(mInWatts);
    }

    @Override
    public String toString() {
        return mInWatts + " W";
    }
}
