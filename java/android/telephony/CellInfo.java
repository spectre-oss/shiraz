// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public abstract class CellInfo implements Parcelable {
    public static final int CONNECTION_NONE = 0;
    public static final int CONNECTION_PRIMARY_SERVING = 1;
    public static final int CONNECTION_SECONDARY_SERVING = 2;
    public static final int CONNECTION_UNKNOWN = Integer.MAX_VALUE;
    public static final int UNAVAILABLE = Integer.MAX_VALUE;
    public static final long UNAVAILABLE_LONG = Long.MAX_VALUE;

    static final int TYPE_GSM = 1;
    static final int TYPE_CDMA = 2;
    static final int TYPE_LTE = 3;
    static final int TYPE_WCDMA = 4;
    static final int TYPE_TDSCDMA = 5;
    static final int TYPE_NR = 6;

    private int mCellConnectionStatus;
    private boolean mRegistered;
    private long mTimeStamp;

    CellInfo() {
        mRegistered = false;
        mTimeStamp = Long.MAX_VALUE;
        mCellConnectionStatus = CONNECTION_NONE;
    }

    CellInfo(int connectionStatus, boolean registered, long timeStampNanos) {
        mCellConnectionStatus = connectionStatus;
        mRegistered = registered;
        mTimeStamp = timeStampNanos;
    }

    CellInfo(Parcel in) {
        mRegistered = in.readInt() == 1;
        mTimeStamp = in.readLong();
        mCellConnectionStatus = in.readInt();
    }

    public boolean isRegistered() {
        return mRegistered;
    }

    public long getTimestampMillis() {
        if (mTimeStamp == UNAVAILABLE_LONG) {
            return UNAVAILABLE_LONG;
        }
        return mTimeStamp / 1000000L;
    }

    @Deprecated
    public long getTimeStamp() {
        return mTimeStamp;
    }

    public abstract CellIdentity getCellIdentity();

    public abstract CellSignalStrength getCellSignalStrength();

    public int getCellConnectionStatus() {
        return mCellConnectionStatus;
    }

    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(mCellConnectionStatus),
                Boolean.valueOf(mRegistered),
                Long.valueOf(mTimeStamp));
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CellInfo)) {
            return false;
        }
        CellInfo other = (CellInfo) o;
        return mCellConnectionStatus == other.mCellConnectionStatus
                && mRegistered == other.mRegistered
                && mTimeStamp == other.mTimeStamp;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("mRegistered=").append(mRegistered ? "YES" : "NO");
        sb.append(" mTimeStamp=").append(mTimeStamp).append("ns");
        sb.append(" mCellConnectionStatus=").append(mCellConnectionStatus);
        return sb.toString();
    }

    public int describeContents() {
        return 0;
    }

    public abstract void writeToParcel(Parcel dest, int flags);

    void writeToParcelHeader(Parcel dest, int flags, int type) {
        dest.writeInt(type);
        dest.writeInt(mRegistered ? 1 : 0);
        dest.writeLong(mTimeStamp);
        dest.writeInt(mCellConnectionStatus);
    }

    public static final Parcelable.Creator<CellInfo> CREATOR =
            new Parcelable.Creator<CellInfo>() {
                public CellInfo createFromParcel(Parcel in) {
                    int type = in.readInt();
                    switch (type) {
                        case TYPE_GSM:
                            return CellInfoGsm.createFromParcelBody(in);
                        case TYPE_CDMA:
                            return CellInfoCdma.createFromParcelBody(in);
                        case TYPE_LTE:
                            return CellInfoLte.createFromParcelBody(in);
                        case TYPE_WCDMA:
                            return CellInfoWcdma.createFromParcelBody(in);
                        case TYPE_TDSCDMA:
                            return CellInfoTdscdma.createFromParcelBody(in);
                        case TYPE_NR:
                            return CellInfoNr.createFromParcelBody(in);
                        default:
                            throw new RuntimeException("Bad CellInfo Parcel: " + type);
                    }
                }

                public CellInfo[] newArray(int size) {
                    return new CellInfo[size];
                }
            };
}
