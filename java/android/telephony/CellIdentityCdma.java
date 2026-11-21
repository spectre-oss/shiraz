// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class CellIdentityCdma extends CellIdentity {
    private final int mNetworkId;
    private final int mSystemId;
    private final int mBasestationId;
    private final int mLongitude;
    private final int mLatitude;

    CellIdentityCdma() {
        this(
                CellInfo.UNAVAILABLE,
                CellInfo.UNAVAILABLE,
                CellInfo.UNAVAILABLE,
                CellInfo.UNAVAILABLE,
                CellInfo.UNAVAILABLE,
                null,
                null);
    }

    CellIdentityCdma(
            int nid, int sid, int bid, int lon, int lat, String alphal, String alphas) {
        super(TYPE_CDMA, null, null, alphal, alphas);
        mNetworkId = nid;
        mSystemId = sid;
        mBasestationId = bid;
        mLongitude = lon;
        mLatitude = lat;
    }

    private CellIdentityCdma(Parcel in) {
        super(TYPE_CDMA, in);
        mNetworkId = in.readInt();
        mSystemId = in.readInt();
        mBasestationId = in.readInt();
        mLongitude = in.readInt();
        mLatitude = in.readInt();
    }

    static CellIdentityCdma createFromParcelBody(Parcel in) {
        return new CellIdentityCdma(in);
    }

    public int getNetworkId() {
        return mNetworkId;
    }

    public int getSystemId() {
        return mSystemId;
    }

    public int getBasestationId() {
        return mBasestationId;
    }

    public int getLongitude() {
        return mLongitude;
    }

    public int getLatitude() {
        return mLatitude;
    }

    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(mNetworkId),
                Integer.valueOf(mSystemId),
                Integer.valueOf(mBasestationId),
                Integer.valueOf(mLatitude),
                Integer.valueOf(mLongitude),
                Integer.valueOf(super.hashCode()));
    }

    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CellIdentityCdma)) return false;
        CellIdentityCdma o = (CellIdentityCdma) other;
        return mNetworkId == o.mNetworkId
                && mSystemId == o.mSystemId
                && mBasestationId == o.mBasestationId
                && mLatitude == o.mLatitude
                && mLongitude == o.mLongitude
                && super.equals(other);
    }

    public String toString() {
        return "CellIdentityCdma:{"
                + " mNetworkId=" + mNetworkId
                + " mSystemId=" + mSystemId
                + " mBasestationId=" + mBasestationId
                + " mLongitude=" + mLongitude
                + " mLatitude=" + mLatitude
                + " mAlphaLong=" + mAlphaLong
                + " mAlphaShort=" + mAlphaShort + "}";
    }

    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, TYPE_CDMA);
        dest.writeInt(mNetworkId);
        dest.writeInt(mSystemId);
        dest.writeInt(mBasestationId);
        dest.writeInt(mLongitude);
        dest.writeInt(mLatitude);
    }

    public static final Parcelable.Creator<CellIdentityCdma> CREATOR =
            new Parcelable.Creator<CellIdentityCdma>() {
                public CellIdentityCdma createFromParcel(Parcel in) {
                    in.readInt();
                    return createFromParcelBody(in);
                }

                public CellIdentityCdma[] newArray(int size) {
                    return new CellIdentityCdma[size];
                }
            };
}
