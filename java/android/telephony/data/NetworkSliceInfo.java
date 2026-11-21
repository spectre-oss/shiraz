// SPDX-License-Identifier: GPL-3.0-only
package android.telephony.data;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class NetworkSliceInfo implements Parcelable {
    public static final int SLICE_SERVICE_TYPE_NONE = 0;
    public static final int SLICE_SERVICE_TYPE_EMBB = 1;
    public static final int SLICE_SERVICE_TYPE_URLLC = 2;
    public static final int SLICE_SERVICE_TYPE_MIOT = 3;

    public static final int SLICE_DIFFERENTIATOR_NO_SLICE = -1;
    private static final int MAX_SLICE_DIFFERENTIATOR = 0xFFFFFE;

    public static final int SLICE_STATUS_UNKNOWN = 0;
    public static final int SLICE_STATUS_CONFIGURED = 1;
    public static final int SLICE_STATUS_ALLOWED = 2;
    public static final int SLICE_STATUS_REJECTED_NOT_AVAILABLE_IN_PLMN = 3;
    public static final int SLICE_STATUS_REJECTED_NOT_AVAILABLE_IN_REGISTERED_AREA = 4;
    public static final int SLICE_STATUS_DEFAULT_CONFIGURED = 5;

    private final int mSliceServiceType;
    private final int mSliceDifferentiator;
    private final int mMappedHplmnSliceServiceType;
    private final int mMappedHplmnSliceDifferentiator;
    private final int mStatus;

    private NetworkSliceInfo(
            int sliceServiceType,
            int sliceDifferentiator,
            int mappedHplmnSliceServiceType,
            int mappedHplmnSliceDifferentiator,
            int status) {
        mSliceServiceType = sliceServiceType;
        mSliceDifferentiator = sliceDifferentiator;
        mMappedHplmnSliceServiceType = mappedHplmnSliceServiceType;
        mMappedHplmnSliceDifferentiator = mappedHplmnSliceDifferentiator;
        mStatus = status;
    }

    private NetworkSliceInfo(Parcel in) {
        mSliceServiceType = in.readInt();
        mSliceDifferentiator = in.readInt();
        mMappedHplmnSliceServiceType = in.readInt();
        mMappedHplmnSliceDifferentiator = in.readInt();
        mStatus = in.readInt();
    }

    public int getSliceServiceType() {
        return mSliceServiceType;
    }

    public int getSliceDifferentiator() {
        return mSliceDifferentiator;
    }

    public int getMappedHplmnSliceServiceType() {
        return mMappedHplmnSliceServiceType;
    }

    public int getMappedHplmnSliceDifferentiator() {
        return mMappedHplmnSliceDifferentiator;
    }

    public int getStatus() {
        return mStatus;
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mSliceServiceType);
        dest.writeInt(mSliceDifferentiator);
        dest.writeInt(mMappedHplmnSliceServiceType);
        dest.writeInt(mMappedHplmnSliceDifferentiator);
        dest.writeInt(mStatus);
    }

    public String toString() {
        return "SliceInfo{"
                + "mSliceServiceType=" + mSliceServiceType
                + ", mSliceDifferentiator=" + mSliceDifferentiator
                + ", mMappedHplmnSliceServiceType=" + mMappedHplmnSliceServiceType
                + ", mMappedHplmnSliceDifferentiator=" + mMappedHplmnSliceDifferentiator
                + ", mStatus=" + mStatus
                + '}';
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NetworkSliceInfo)) return false;
        NetworkSliceInfo that = (NetworkSliceInfo) o;
        return mSliceServiceType == that.mSliceServiceType
                && mSliceDifferentiator == that.mSliceDifferentiator
                && mMappedHplmnSliceServiceType == that.mMappedHplmnSliceServiceType
                && mMappedHplmnSliceDifferentiator == that.mMappedHplmnSliceDifferentiator
                && mStatus == that.mStatus;
    }

    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(mSliceServiceType),
                Integer.valueOf(mSliceDifferentiator),
                Integer.valueOf(mMappedHplmnSliceServiceType),
                Integer.valueOf(mMappedHplmnSliceDifferentiator),
                Integer.valueOf(mStatus));
    }

    public static final Parcelable.Creator<NetworkSliceInfo> CREATOR =
            new Parcelable.Creator<NetworkSliceInfo>() {
                public NetworkSliceInfo createFromParcel(Parcel source) {
                    return new NetworkSliceInfo(source);
                }

                public NetworkSliceInfo[] newArray(int size) {
                    return new NetworkSliceInfo[size];
                }
            };

    public static final class Builder {
        private int mSliceServiceType = SLICE_SERVICE_TYPE_NONE;
        private int mSliceDifferentiator = SLICE_DIFFERENTIATOR_NO_SLICE;
        private int mMappedHplmnSliceServiceType = SLICE_SERVICE_TYPE_NONE;
        private int mMappedHplmnSliceDifferentiator = SLICE_DIFFERENTIATOR_NO_SLICE;
        private int mStatus = SLICE_STATUS_UNKNOWN;

        public Builder() {}

        public Builder setSliceServiceType(int mSliceServiceType) {
            this.mSliceServiceType = mSliceServiceType;
            return this;
        }

        public Builder setSliceDifferentiator(int sliceDifferentiator) {
            if (sliceDifferentiator < SLICE_DIFFERENTIATOR_NO_SLICE
                    || sliceDifferentiator > MAX_SLICE_DIFFERENTIATOR) {
                throw new IllegalArgumentException("The slice diffentiator value is out of range");
            }
            this.mSliceDifferentiator = sliceDifferentiator;
            return this;
        }

        public Builder setMappedHplmnSliceServiceType(int mappedHplmnSliceServiceType) {
            this.mMappedHplmnSliceServiceType = mappedHplmnSliceServiceType;
            return this;
        }

        public Builder setMappedHplmnSliceDifferentiator(int mappedHplmnSliceDifferentiator) {
            if (mappedHplmnSliceDifferentiator < SLICE_DIFFERENTIATOR_NO_SLICE
                    || mappedHplmnSliceDifferentiator > MAX_SLICE_DIFFERENTIATOR) {
                throw new IllegalArgumentException("The slice diffentiator value is out of range");
            }
            this.mMappedHplmnSliceDifferentiator = mappedHplmnSliceDifferentiator;
            return this;
        }

        public Builder setStatus(int status) {
            if (status < SLICE_STATUS_UNKNOWN || status > SLICE_STATUS_DEFAULT_CONFIGURED) {
                throw new IllegalArgumentException("The slice status is not valid");
            }
            this.mStatus = status;
            return this;
        }

        public NetworkSliceInfo build() {
            return new NetworkSliceInfo(
                    mSliceServiceType,
                    mSliceDifferentiator,
                    mMappedHplmnSliceServiceType,
                    mMappedHplmnSliceDifferentiator,
                    mStatus);
        }
    }
}
