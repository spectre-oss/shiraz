// SPDX-License-Identifier: GPL-3.0-only
package android.media.metrics;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class NetworkEvent extends Event implements Parcelable {
    public static final int NETWORK_TYPE_UNKNOWN = 0;
    public static final int NETWORK_TYPE_OTHER = 1;
    public static final int NETWORK_TYPE_WIFI = 2;
    public static final int NETWORK_TYPE_ETHERNET = 3;
    public static final int NETWORK_TYPE_2G = 4;
    public static final int NETWORK_TYPE_3G = 5;
    public static final int NETWORK_TYPE_4G = 6;
    public static final int NETWORK_TYPE_5G_NSA = 7;
    public static final int NETWORK_TYPE_5G_SA = 8;
    public static final int NETWORK_TYPE_OFFLINE = 9;

    public static final class Builder {
        private int mNetworkType = NETWORK_TYPE_UNKNOWN;
        private long mTimeSinceCreatedMillis = -1L;
        private Bundle mMetricsBundle = new Bundle();

        public Builder() {
        }

        public Builder setNetworkType(int value) {
            mNetworkType = value;
            return this;
        }

        public Builder setTimeSinceCreatedMillis(long value) {
            mTimeSinceCreatedMillis = value;
            return this;
        }

        public Builder setMetricsBundle(Bundle metricsBundle) {
            mMetricsBundle = metricsBundle != null ? new Bundle(metricsBundle) : new Bundle();
            return this;
        }

        public NetworkEvent build() {
            return new NetworkEvent(mNetworkType, mTimeSinceCreatedMillis, mMetricsBundle);
        }
    }

    private final int mNetworkType;

    NetworkEvent(int type, long timeSinceCreatedMillis, Bundle extras) {
        super(timeSinceCreatedMillis, extras);
        mNetworkType = type;
    }

    public int getNetworkType() {
        return mNetworkType;
    }

    @Override
    public long getTimeSinceCreatedMillis() {
        return mTimeSinceCreatedMillis;
    }

    @Override
    public Bundle getMetricsBundle() {
        return super.getMetricsBundle();
    }

    @Override
    public String toString() {
        return "NetworkEvent { networkType = " + mNetworkType
                + ", timeSinceCreatedMillis = " + mTimeSinceCreatedMillis + " }";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NetworkEvent)) return false;
        NetworkEvent that = (NetworkEvent) o;
        return mNetworkType == that.mNetworkType
                && mTimeSinceCreatedMillis == that.mTimeSinceCreatedMillis;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mNetworkType, mTimeSinceCreatedMillis);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mNetworkType);
        dest.writeLong(mTimeSinceCreatedMillis);
        dest.writeBundle(mMetricsBundle);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<NetworkEvent> CREATOR =
            new Parcelable.Creator<NetworkEvent>() {
                @Override
                public NetworkEvent createFromParcel(Parcel in) {
                    int type = in.readInt();
                    long time = in.readLong();
                    Bundle extras = in.readBundle();
                    return new NetworkEvent(type, time, extras);
                }

                @Override
                public NetworkEvent[] newArray(int size) {
                    return new NetworkEvent[size];
                }
            };
}
