// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class PhysicalChannelConfig implements Parcelable {
    public static final int CONNECTION_PRIMARY_SERVING = 1;
    public static final int CONNECTION_SECONDARY_SERVING = 2;
    public static final int CONNECTION_UNKNOWN = -1;
    public static final int CELL_BANDWIDTH_UNKNOWN = 0;
    public static final int CHANNEL_NUMBER_UNKNOWN = Integer.MAX_VALUE;
    public static final int PHYSICAL_CELL_ID_UNKNOWN = -1;
    public static final int PHYSICAL_CELL_ID_MAXIMUM_VALUE = 1007;
    public static final int BAND_UNKNOWN = 0;
    public static final int FREQUENCY_UNKNOWN = -1;

    private final int mCellConnectionStatus;
    private final int mNetworkType;
    private final int mDownlinkChannelNumber;
    private final int mUplinkChannelNumber;
    private final int mCellBandwidthDownlinkKhz;
    private final int mCellBandwidthUplinkKhz;
    private final int mPhysicalCellId;
    private final int mBand;
    private final int mDownlinkFrequency;
    private final int mUplinkFrequency;

    PhysicalChannelConfig() {
        mCellConnectionStatus = CONNECTION_UNKNOWN;
        mNetworkType = 0;
        mDownlinkChannelNumber = CHANNEL_NUMBER_UNKNOWN;
        mUplinkChannelNumber = CHANNEL_NUMBER_UNKNOWN;
        mCellBandwidthDownlinkKhz = CELL_BANDWIDTH_UNKNOWN;
        mCellBandwidthUplinkKhz = CELL_BANDWIDTH_UNKNOWN;
        mPhysicalCellId = PHYSICAL_CELL_ID_UNKNOWN;
        mBand = BAND_UNKNOWN;
        mDownlinkFrequency = FREQUENCY_UNKNOWN;
        mUplinkFrequency = FREQUENCY_UNKNOWN;
    }

    private PhysicalChannelConfig(Parcel in) {
        mCellConnectionStatus = in.readInt();
        mNetworkType = in.readInt();
        mDownlinkChannelNumber = in.readInt();
        mUplinkChannelNumber = in.readInt();
        mCellBandwidthDownlinkKhz = in.readInt();
        mCellBandwidthUplinkKhz = in.readInt();
        mPhysicalCellId = in.readInt();
        mBand = in.readInt();
        mDownlinkFrequency = in.readInt();
        mUplinkFrequency = in.readInt();
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mCellConnectionStatus);
        dest.writeInt(mNetworkType);
        dest.writeInt(mDownlinkChannelNumber);
        dest.writeInt(mUplinkChannelNumber);
        dest.writeInt(mCellBandwidthDownlinkKhz);
        dest.writeInt(mCellBandwidthUplinkKhz);
        dest.writeInt(mPhysicalCellId);
        dest.writeInt(mBand);
        dest.writeInt(mDownlinkFrequency);
        dest.writeInt(mUplinkFrequency);
    }

    public int getCellBandwidthDownlinkKhz() {
        return mCellBandwidthDownlinkKhz;
    }

    public int getCellBandwidthUplinkKhz() {
        return mCellBandwidthUplinkKhz;
    }

    public int getDownlinkChannelNumber() {
        return mDownlinkChannelNumber;
    }

    public int getUplinkChannelNumber() {
        return mUplinkChannelNumber;
    }

    public int getBand() {
        return mBand;
    }

    public int getDownlinkFrequencyKhz() {
        return mDownlinkFrequency;
    }

    public int getUplinkFrequencyKhz() {
        return mUplinkFrequency;
    }

    public int getPhysicalCellId() {
        return mPhysicalCellId;
    }

    public int getNetworkType() {
        return mNetworkType;
    }

    public int getConnectionStatus() {
        return mCellConnectionStatus;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PhysicalChannelConfig)) {
            return false;
        }
        PhysicalChannelConfig config = (PhysicalChannelConfig) o;
        return mCellConnectionStatus == config.mCellConnectionStatus
                && mNetworkType == config.mNetworkType
                && mDownlinkChannelNumber == config.mDownlinkChannelNumber
                && mUplinkChannelNumber == config.mUplinkChannelNumber
                && mCellBandwidthDownlinkKhz == config.mCellBandwidthDownlinkKhz
                && mCellBandwidthUplinkKhz == config.mCellBandwidthUplinkKhz
                && mPhysicalCellId == config.mPhysicalCellId
                && mBand == config.mBand
                && mDownlinkFrequency == config.mDownlinkFrequency
                && mUplinkFrequency == config.mUplinkFrequency;
    }

    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(mCellConnectionStatus),
                Integer.valueOf(mNetworkType),
                Integer.valueOf(mDownlinkChannelNumber),
                Integer.valueOf(mUplinkChannelNumber),
                Integer.valueOf(mCellBandwidthDownlinkKhz),
                Integer.valueOf(mCellBandwidthUplinkKhz),
                Integer.valueOf(mPhysicalCellId),
                Integer.valueOf(mBand),
                Integer.valueOf(mDownlinkFrequency),
                Integer.valueOf(mUplinkFrequency));
    }

    public String toString() {
        return "{mConnectionStatus=" + mCellConnectionStatus
                + ",mNetworkType=" + mNetworkType
                + ",mCellBandwidthDownlinkKhz=" + mCellBandwidthDownlinkKhz
                + ",mCellBandwidthUplinkKhz=" + mCellBandwidthUplinkKhz
                + ",mDownlinkChannelNumber=" + mDownlinkChannelNumber
                + ",mUplinkChannelNumber=" + mUplinkChannelNumber
                + ",mPhysicalCellId=" + mPhysicalCellId
                + ",mBand=" + mBand
                + ",mDownlinkFrequency=" + mDownlinkFrequency
                + ",mUplinkFrequency=" + mUplinkFrequency + "}";
    }

    public static final Parcelable.Creator<PhysicalChannelConfig> CREATOR =
            new Parcelable.Creator<PhysicalChannelConfig>() {
                public PhysicalChannelConfig createFromParcel(Parcel in) {
                    return new PhysicalChannelConfig(in);
                }

                public PhysicalChannelConfig[] newArray(int size) {
                    return new PhysicalChannelConfig[size];
                }
            };
}
