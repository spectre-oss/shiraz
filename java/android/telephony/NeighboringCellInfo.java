// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;

@Deprecated
public class NeighboringCellInfo implements Parcelable {
    @Deprecated public static final int UNKNOWN_RSSI = 99;
    @Deprecated public static final int UNKNOWN_CID = -1;

    private int mRssi;
    private int mCid;
    private int mLac;
    private int mPsc;
    private int mNetworkType;

    @Deprecated
    public NeighboringCellInfo() {
        mRssi = UNKNOWN_RSSI;
        mLac = UNKNOWN_CID;
        mCid = UNKNOWN_CID;
        mPsc = UNKNOWN_CID;
        mNetworkType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
    }

    @Deprecated
    public NeighboringCellInfo(int rssi, int cid) {
        mRssi = rssi;
        mCid = cid;
        mLac = UNKNOWN_CID;
        mPsc = UNKNOWN_CID;
        mNetworkType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
    }

    @Deprecated
    public NeighboringCellInfo(int rssi, String location, int radioType) {
        mRssi = rssi;
        mNetworkType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
        mPsc = UNKNOWN_CID;
        mLac = UNKNOWN_CID;
        mCid = UNKNOWN_CID;
        if (location == null) {
            return;
        }
        int l = location.length();
        if (l > 8) {
            return;
        }
        if (l < 8) {
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 8 - l; i++) {
                sb.append('0');
            }
            sb.append(location);
            location = sb.toString();
        }
        try {
            switch (radioType) {
                case TelephonyManager.NETWORK_TYPE_GPRS:
                case TelephonyManager.NETWORK_TYPE_EDGE:
                    mNetworkType = radioType;
                    if (!location.equalsIgnoreCase("FFFFFFFF")) {
                        mCid = Integer.parseInt(location.substring(4), 16);
                        mLac = Integer.parseInt(location.substring(0, 4), 16);
                    }
                    break;
                case TelephonyManager.NETWORK_TYPE_UMTS:
                case TelephonyManager.NETWORK_TYPE_HSDPA:
                case TelephonyManager.NETWORK_TYPE_HSUPA:
                case TelephonyManager.NETWORK_TYPE_HSPA:
                    mNetworkType = radioType;
                    if (!location.equalsIgnoreCase("FFFFFFFF")) {
                        mPsc = Integer.parseInt(location, 16);
                    }
                    break;
                default:
                    break;
            }
        } catch (NumberFormatException e) {
            mPsc = UNKNOWN_CID;
            mLac = UNKNOWN_CID;
            mCid = UNKNOWN_CID;
            mNetworkType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
        }
    }

    @Deprecated
    public NeighboringCellInfo(Parcel in) {
        mRssi = in.readInt();
        mLac = in.readInt();
        mCid = in.readInt();
        mPsc = in.readInt();
        mNetworkType = in.readInt();
    }

    @Deprecated
    public int getRssi() {
        return mRssi;
    }

    @Deprecated
    public int getLac() {
        return mLac;
    }

    @Deprecated
    public int getCid() {
        return mCid;
    }

    @Deprecated
    public int getPsc() {
        return mPsc;
    }

    @Deprecated
    public int getNetworkType() {
        return mNetworkType;
    }

    @Deprecated
    public void setCid(int cid) {
        mCid = cid;
    }

    @Deprecated
    public void setRssi(int rssi) {
        mRssi = rssi;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("[");
        if (mPsc != UNKNOWN_CID) {
            sb.append(Integer.toHexString(mPsc))
                    .append("@")
                    .append(mRssi == UNKNOWN_RSSI ? "-" : Integer.valueOf(mRssi));
        } else if (mLac != UNKNOWN_CID && mCid != UNKNOWN_CID) {
            sb.append(Integer.toHexString(mLac))
                    .append(Integer.toHexString(mCid))
                    .append("@")
                    .append(mRssi == UNKNOWN_RSSI ? "-" : Integer.valueOf(mRssi));
        }
        sb.append("]");
        return sb.toString();
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mRssi);
        dest.writeInt(mLac);
        dest.writeInt(mCid);
        dest.writeInt(mPsc);
        dest.writeInt(mNetworkType);
    }

    @Deprecated
    public static final Parcelable.Creator<NeighboringCellInfo> CREATOR =
            new Parcelable.Creator<NeighboringCellInfo>() {
                public NeighboringCellInfo createFromParcel(Parcel in) {
                    return new NeighboringCellInfo(in);
                }

                public NeighboringCellInfo[] newArray(int size) {
                    return new NeighboringCellInfo[size];
                }
            };
}
