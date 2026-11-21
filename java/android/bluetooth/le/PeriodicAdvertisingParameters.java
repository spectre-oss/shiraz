// SPDX-License-Identifier: GPL-3.0-only
package android.bluetooth.le;

import android.os.Parcel;
import android.os.Parcelable;

public final class PeriodicAdvertisingParameters implements Parcelable {
    private static final int INTERVAL_MIN = 80;
    private static final int INTERVAL_MAX = 65519;

    private final boolean mIncludeTxPower;
    private final int mInterval;

    PeriodicAdvertisingParameters(boolean includeTxPower, int interval) {
        mIncludeTxPower = includeTxPower;
        mInterval = interval;
    }

    public boolean getIncludeTxPower() {
        return mIncludeTxPower;
    }

    public int getInterval() {
        return mInterval;
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mIncludeTxPower ? 1 : 0);
        dest.writeInt(mInterval);
    }

    public static final Parcelable.Creator<PeriodicAdvertisingParameters> CREATOR = new Parcelable.Creator<PeriodicAdvertisingParameters>() {
        public PeriodicAdvertisingParameters createFromParcel(Parcel in) {
            boolean includeTx = in.readInt() != 0;
            int interval = in.readInt();
            return new PeriodicAdvertisingParameters(includeTx, interval);
        }

        public PeriodicAdvertisingParameters[] newArray(int size) {
            return new PeriodicAdvertisingParameters[size];
        }
    };

    public static final class Builder {
        private boolean mIncludeTxPower = false;
        private int mInterval = INTERVAL_MAX;

        public Builder() {}

        public Builder setIncludeTxPower(boolean includeTxPower) {
            mIncludeTxPower = includeTxPower;
            return this;
        }

        public Builder setInterval(int interval) {
            if (interval < INTERVAL_MIN || interval > INTERVAL_MAX) {
                throw new IllegalArgumentException("Invalid interval (must be " + INTERVAL_MIN + "-" + INTERVAL_MAX + ")");
            }
            mInterval = interval;
            return this;
        }

        public PeriodicAdvertisingParameters build() {
            return new PeriodicAdvertisingParameters(mIncludeTxPower, mInterval);
        }
    }
}
