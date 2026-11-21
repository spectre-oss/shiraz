// SPDX-License-Identifier: GPL-3.0-only
package android.location;

import android.os.Parcel;
import android.os.Parcelable;

public final class GnssNavigationMessage implements Parcelable {
    private static final byte[] EMPTY_ARRAY = new byte[0];

    public static final int TYPE_UNKNOWN = 0;
    public static final int TYPE_GPS_L1CA = 0x0101;
    public static final int TYPE_GPS_L2CNAV = 0x0102;
    public static final int TYPE_GPS_L5CNAV = 0x0103;
    public static final int TYPE_GPS_CNAV2 = 0x0104;
    public static final int TYPE_SBS = 0x0201;
    public static final int TYPE_GLO_L1CA = 0x0301;
    public static final int TYPE_QZS_L1CA = 0x0401;
    public static final int TYPE_BDS_D1 = 0x0501;
    public static final int TYPE_BDS_D2 = 0x0502;
    public static final int TYPE_BDS_CNAV1 = 0x0503;
    public static final int TYPE_BDS_CNAV2 = 0x0504;
    public static final int TYPE_GAL_I = 0x0601;
    public static final int TYPE_GAL_F = 0x0602;
    public static final int TYPE_IRN_L5CA = 0x0701;
    public static final int TYPE_IRN_L5 = 0x0702;
    public static final int TYPE_IRN_L1 = 0x0703;

    public static final int STATUS_UNKNOWN = 0;
    public static final int STATUS_PARITY_PASSED = 1;
    public static final int STATUS_PARITY_REBUILT = 2;

    public abstract static class Callback {
        public static final int STATUS_NOT_SUPPORTED = 0;
        public static final int STATUS_READY = 1;
        public static final int STATUS_LOCATION_DISABLED = 2;

        public Callback() {}

        public void onGnssNavigationMessageReceived(GnssNavigationMessage event) {}

        public void onStatusChanged(int status) {}
    }

    private int mType;
    private int mSvid;
    private int mMessageId;
    private int mSubmessageId;
    private byte[] mData;
    private int mStatus;

    public GnssNavigationMessage() {
        mType = TYPE_UNKNOWN;
        mMessageId = -1;
        mSubmessageId = -1;
        mData = EMPTY_ARRAY;
        mStatus = STATUS_UNKNOWN;
    }

    public int getType() {
        return mType;
    }

    public void setType(int value) {
        mType = value;
    }

    public int getSvid() {
        return mSvid;
    }

    public void setSvid(int value) {
        mSvid = value;
    }

    public int getMessageId() {
        return mMessageId;
    }

    public void setMessageId(int value) {
        mMessageId = value;
    }

    public int getSubmessageId() {
        return mSubmessageId;
    }

    public void setSubmessageId(int value) {
        mSubmessageId = value;
    }

    public byte[] getData() {
        return mData;
    }

    public void setData(byte[] value) {
        if (value == null) {
            throw new IllegalArgumentException("Data must be a non-null array");
        }
        mData = value;
    }

    public int getStatus() {
        return mStatus;
    }

    public void setStatus(int value) {
        mStatus = value;
    }

    public static final Parcelable.Creator<GnssNavigationMessage> CREATOR =
            new Parcelable.Creator<GnssNavigationMessage>() {
                @Override
                public GnssNavigationMessage createFromParcel(Parcel parcel) {
                    GnssNavigationMessage navigationMessage = new GnssNavigationMessage();
                    navigationMessage.setType(parcel.readInt());
                    navigationMessage.setSvid(parcel.readInt());
                    navigationMessage.setMessageId(parcel.readInt());
                    navigationMessage.setSubmessageId(parcel.readInt());
                    byte[] data = parcel.createByteArray();
                    navigationMessage.setData(data != null ? data : EMPTY_ARRAY);
                    navigationMessage.setStatus(parcel.readInt());
                    return navigationMessage;
                }

                @Override
                public GnssNavigationMessage[] newArray(int size) {
                    return new GnssNavigationMessage[size];
                }
            };

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(mType);
        parcel.writeInt(mSvid);
        parcel.writeInt(mMessageId);
        parcel.writeInt(mSubmessageId);
        parcel.writeByteArray(mData);
        parcel.writeInt(mStatus);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String toString() {
        return "GnssNavigationMessage[Type=" + mType
                + ", Svid=" + mSvid
                + ", Id=" + mMessageId
                + ", SubId=" + mSubmessageId
                + ", DataLength=" + mData.length
                + ", Status=" + mStatus + "]";
    }
}
