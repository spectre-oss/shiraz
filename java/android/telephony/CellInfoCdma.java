// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class CellInfoCdma extends CellInfo implements Parcelable {
    private CellIdentityCdma mCellIdentityCdma;
    private CellSignalStrengthCdma mCellSignalStrengthCdma;

    CellInfoCdma() {
        super();
        mCellIdentityCdma = new CellIdentityCdma();
        mCellSignalStrengthCdma = new CellSignalStrengthCdma();
    }

    CellInfoCdma(int connectionStatus, boolean registered, long timeStampNanos,
            CellIdentityCdma cellIdentityCdma, CellSignalStrengthCdma cellSignalStrengthCdma) {
        super(connectionStatus, registered, timeStampNanos);
        mCellIdentityCdma = cellIdentityCdma != null ? cellIdentityCdma : new CellIdentityCdma();
        mCellSignalStrengthCdma = cellSignalStrengthCdma != null
                ? cellSignalStrengthCdma : new CellSignalStrengthCdma();
    }

    private CellInfoCdma(Parcel in) {
        super(in);
        mCellIdentityCdma = CellIdentityCdma.CREATOR.createFromParcel(in);
        mCellSignalStrengthCdma = CellSignalStrengthCdma.CREATOR.createFromParcel(in);
    }

    public CellIdentityCdma getCellIdentity() {
        return mCellIdentityCdma;
    }

    public CellSignalStrengthCdma getCellSignalStrength() {
        return mCellSignalStrengthCdma;
    }

    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(super.hashCode()),
                mCellIdentityCdma,
                mCellSignalStrengthCdma);
    }

    public boolean equals(Object other) {
        if (!super.equals(other)) {
            return false;
        }
        if (!(other instanceof CellInfoCdma)) {
            return false;
        }
        CellInfoCdma o = (CellInfoCdma) other;
        return Objects.equals(mCellIdentityCdma, o.mCellIdentityCdma)
                && Objects.equals(mCellSignalStrengthCdma, o.mCellSignalStrengthCdma);
    }

    public String toString() {
        return "CellInfoCdma:{" + super.toString()
                + " " + mCellIdentityCdma
                + " " + mCellSignalStrengthCdma + "}";
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        writeToParcelHeader(dest, flags, TYPE_CDMA);
        mCellIdentityCdma.writeToParcel(dest, flags);
        mCellSignalStrengthCdma.writeToParcel(dest, flags);
    }

    static CellInfoCdma createFromParcelBody(Parcel in) {
        return new CellInfoCdma(in);
    }

    public static final Parcelable.Creator<CellInfoCdma> CREATOR =
            new Parcelable.Creator<CellInfoCdma>() {
                public CellInfoCdma createFromParcel(Parcel in) {
                    in.readInt();
                    return createFromParcelBody(in);
                }

                public CellInfoCdma[] newArray(int size) {
                    return new CellInfoCdma[size];
                }
            };
}
