// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi;

import android.net.MacAddress;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class MloLink implements Parcelable {
    public static final int INVALID_MLO_LINK_ID = -1;
    public static final int MLO_LINK_STATE_INVALID = 0;
    public static final int MLO_LINK_STATE_UNASSOCIATED = 1;
    public static final int MLO_LINK_STATE_IDLE = 2;
    public static final int MLO_LINK_STATE_ACTIVE = 3;

    private int mLinkId = INVALID_MLO_LINK_ID;
    private MacAddress mApMacAddress;
    private MacAddress mStaMacAddress;
    private int mState = MLO_LINK_STATE_UNASSOCIATED;
    private int mBand = ScanResult.WIFI_BAND_24_GHZ;
    private int mChannel;
    private int mRssi = -127;
    private int mRxLinkSpeed = -1;
    private int mTxLinkSpeed = -1;

    public MloLink() {
    }

    public int getBand() {
        return mBand;
    }

    public int getChannel() {
        return mChannel;
    }

    public int getLinkId() {
        return mLinkId;
    }

    public int getState() {
        return mState;
    }

    public MacAddress getApMacAddress() {
        return mApMacAddress;
    }

    public MacAddress getStaMacAddress() {
        return mStaMacAddress;
    }

    public int getRxLinkSpeedMbps() {
        return mRxLinkSpeed;
    }

    public int getTxLinkSpeedMbps() {
        return mTxLinkSpeed;
    }

    public int getRssi() {
        return mRssi;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MloLink)) return false;
        MloLink that = (MloLink) o;
        return mLinkId == that.mLinkId
                && mState == that.mState
                && mBand == that.mBand
                && mChannel == that.mChannel
                && mRssi == that.mRssi
                && mRxLinkSpeed == that.mRxLinkSpeed
                && mTxLinkSpeed == that.mTxLinkSpeed
                && Objects.equals(mApMacAddress, that.mApMacAddress)
                && Objects.equals(mStaMacAddress, that.mStaMacAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mLinkId, mApMacAddress, mStaMacAddress, mState,
                mBand, mChannel, mRssi, mRxLinkSpeed, mTxLinkSpeed);
    }

    @Override
    public String toString() {
        return "MloLink{linkId=" + mLinkId + ", state=" + mState + ", band=" + mBand
                + ", channel=" + mChannel + "}";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mLinkId);
        dest.writeInt(mState);
        dest.writeInt(mBand);
        dest.writeInt(mChannel);
        dest.writeInt(mRssi);
        dest.writeInt(mRxLinkSpeed);
        dest.writeInt(mTxLinkSpeed);
        dest.writeTypedObject(mApMacAddress, flags);
        dest.writeTypedObject(mStaMacAddress, flags);
    }

    public static final Creator<MloLink> CREATOR = new Creator<MloLink>() {
        @Override
        public MloLink createFromParcel(Parcel in) {
            MloLink link = new MloLink();
            link.mLinkId = in.readInt();
            link.mState = in.readInt();
            link.mBand = in.readInt();
            link.mChannel = in.readInt();
            link.mRssi = in.readInt();
            link.mRxLinkSpeed = in.readInt();
            link.mTxLinkSpeed = in.readInt();
            link.mApMacAddress = in.readTypedObject(MacAddress.CREATOR);
            link.mStaMacAddress = in.readTypedObject(MacAddress.CREATOR);
            return link;
        }

        @Override
        public MloLink[] newArray(int size) {
            return new MloLink[size];
        }
    };
}
