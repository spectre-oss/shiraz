// SPDX-License-Identifier: GPL-3.0-only
package android.bluetooth.le;

import android.os.Parcel;
import android.os.Parcelable;

public final class AdvertiseSettings implements Parcelable {
    public static final int ADVERTISE_MODE_LOW_POWER = 0;
    public static final int ADVERTISE_MODE_BALANCED = 1;
    public static final int ADVERTISE_MODE_LOW_LATENCY = 2;

    public static final int ADVERTISE_TX_POWER_ULTRA_LOW = 0;
    public static final int ADVERTISE_TX_POWER_LOW = 1;
    public static final int ADVERTISE_TX_POWER_MEDIUM = 2;
    public static final int ADVERTISE_TX_POWER_HIGH = 3;

    private static final int LIMITED_ADVERTISING_MAX_MILLIS = 180 * 1000;

    private final int mAdvertiseMode;
    private final int mAdvertiseTxPowerLevel;
    private final int mAdvertiseTimeoutMillis;
    private final boolean mAdvertiseConnectable;
    private final boolean mAdvertiseDiscoverable;

    AdvertiseSettings(int advertiseMode, int advertiseTxPowerLevel, boolean advertiseConnectable,
            boolean advertiseDiscoverable, int advertiseTimeout) {
        mAdvertiseMode = advertiseMode;
        mAdvertiseTxPowerLevel = advertiseTxPowerLevel;
        mAdvertiseConnectable = advertiseConnectable;
        mAdvertiseDiscoverable = advertiseDiscoverable;
        mAdvertiseTimeoutMillis = advertiseTimeout;
    }

    public int getMode() {
        return mAdvertiseMode;
    }

    public int getTxPowerLevel() {
        return mAdvertiseTxPowerLevel;
    }

    public boolean isConnectable() {
        return mAdvertiseConnectable;
    }

    public boolean isDiscoverable() {
        return mAdvertiseDiscoverable;
    }

    public int getTimeout() {
        return mAdvertiseTimeoutMillis;
    }

    public String toString() {
        return "Settings [mAdvertiseMode=" + mAdvertiseMode
                + ", mAdvertiseTxPowerLevel=" + mAdvertiseTxPowerLevel
                + ", mAdvertiseConnectable=" + mAdvertiseConnectable
                + ", mAdvertiseDiscoverable=" + mAdvertiseDiscoverable
                + ", mAdvertiseTimeoutMillis=" + mAdvertiseTimeoutMillis + "]";
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mAdvertiseMode);
        dest.writeInt(mAdvertiseTxPowerLevel);
        dest.writeInt(mAdvertiseConnectable ? 1 : 0);
        dest.writeInt(mAdvertiseTimeoutMillis);
        dest.writeInt(mAdvertiseDiscoverable ? 1 : 0);
    }

    public static final Parcelable.Creator<AdvertiseSettings> CREATOR = new Parcelable.Creator<AdvertiseSettings>() {
        public AdvertiseSettings createFromParcel(Parcel in) {
            int mode = in.readInt();
            int txPower = in.readInt();
            boolean connectable = in.readInt() != 0;
            int timeout = in.readInt();
            boolean discoverable = in.readInt() != 0;
            return new AdvertiseSettings(mode, txPower, connectable, discoverable, timeout);
        }

        public AdvertiseSettings[] newArray(int size) {
            return new AdvertiseSettings[size];
        }
    };

    public static final class Builder {
        private int mMode = ADVERTISE_MODE_LOW_POWER;
        private int mTxPowerLevel = ADVERTISE_TX_POWER_MEDIUM;
        private int mTimeoutMillis;
        private boolean mConnectable = true;
        private boolean mDiscoverable = true;

        public Builder() {}

        public Builder setAdvertiseMode(int advertiseMode) {
            if (advertiseMode < ADVERTISE_MODE_LOW_POWER || advertiseMode > ADVERTISE_MODE_LOW_LATENCY) {
                throw new IllegalArgumentException("unknown mode " + advertiseMode);
            }
            mMode = advertiseMode;
            return this;
        }

        public Builder setTxPowerLevel(int txPowerLevel) {
            if (txPowerLevel < ADVERTISE_TX_POWER_ULTRA_LOW || txPowerLevel > ADVERTISE_TX_POWER_HIGH) {
                throw new IllegalArgumentException("unknown tx power level " + txPowerLevel);
            }
            mTxPowerLevel = txPowerLevel;
            return this;
        }

        public Builder setConnectable(boolean connectable) {
            mConnectable = connectable;
            return this;
        }

        public Builder setDiscoverable(boolean discoverable) {
            mDiscoverable = discoverable;
            return this;
        }

        public Builder setTimeout(int timeoutMillis) {
            if (timeoutMillis < 0 || timeoutMillis > LIMITED_ADVERTISING_MAX_MILLIS) {
                throw new IllegalArgumentException("timeoutMillis invalid (must be 0-" + LIMITED_ADVERTISING_MAX_MILLIS + " milliseconds)");
            }
            mTimeoutMillis = timeoutMillis;
            return this;
        }

        public AdvertiseSettings build() {
            return new AdvertiseSettings(mMode, mTxPowerLevel, mConnectable, mDiscoverable, mTimeoutMillis);
        }
    }
}
