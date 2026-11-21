// SPDX-License-Identifier: GPL-3.0-only
package android.telephony.data;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Arrays;
import java.util.Objects;

public final class TrafficDescriptor implements Parcelable {
    private final String mDnn;
    private final byte[] mOsAppId;

    private TrafficDescriptor(String dnn, byte[] osAppId) {
        mDnn = dnn;
        mOsAppId = osAppId != null ? osAppId.clone() : null;
    }

    private TrafficDescriptor(Parcel in) {
        mDnn = in.readString();
        mOsAppId = in.createByteArray();
    }

    public String getDataNetworkName() {
        return mDnn;
    }

    public byte[] getOsAppId() {
        return mOsAppId != null ? mOsAppId.clone() : null;
    }

    public int describeContents() {
        return 0;
    }

    public String toString() {
        return "TrafficDescriptor={mDnn=" + mDnn
                + ", mOsAppId=" + Arrays.toString(mOsAppId) + "}";
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mDnn);
        dest.writeByteArray(mOsAppId);
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TrafficDescriptor)) return false;
        TrafficDescriptor that = (TrafficDescriptor) o;
        return Objects.equals(mDnn, that.mDnn)
                && Arrays.equals(mOsAppId, that.mOsAppId);
    }

    public int hashCode() {
        return Objects.hash(mDnn, Integer.valueOf(Arrays.hashCode(mOsAppId)));
    }

    public static final Parcelable.Creator<TrafficDescriptor> CREATOR =
            new Parcelable.Creator<TrafficDescriptor>() {
                public TrafficDescriptor createFromParcel(Parcel source) {
                    return new TrafficDescriptor(source);
                }

                public TrafficDescriptor[] newArray(int size) {
                    return new TrafficDescriptor[size];
                }
            };

    public static final class Builder {
        private String mDnn;
        private byte[] mOsAppId;

        public Builder() {}

        public Builder setDataNetworkName(String dnn) {
            mDnn = dnn;
            return this;
        }

        public Builder setOsAppId(byte[] osAppId) {
            mOsAppId = osAppId != null ? osAppId.clone() : null;
            return this;
        }

        public TrafficDescriptor build() {
            if (mDnn == null && mOsAppId == null) {
                throw new IllegalArgumentException("DNN and OS App ID are null");
            }
            return new TrafficDescriptor(mDnn, mOsAppId);
        }
    }
}
