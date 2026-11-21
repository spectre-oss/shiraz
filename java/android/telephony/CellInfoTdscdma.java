// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class CellInfoTdscdma extends CellInfo implements Parcelable {
    private CellIdentityTdscdma mCellIdentityTdscdma;
    private CellSignalStrengthTdscdma mCellSignalStrengthTdscdma;

    CellInfoTdscdma() {
        super();
        mCellIdentityTdscdma = new CellIdentityTdscdma();
        mCellSignalStrengthTdscdma = new CellSignalStrengthTdscdma();
    }

    CellInfoTdscdma(int connectionStatus, boolean registered, long timeStampNanos,
            CellIdentityTdscdma cellIdentityTdscdma,
            CellSignalStrengthTdscdma cellSignalStrengthTdscdma) {
        super(connectionStatus, registered, timeStampNanos);
        mCellIdentityTdscdma = cellIdentityTdscdma != null
                ? cellIdentityTdscdma : new CellIdentityTdscdma();
        mCellSignalStrengthTdscdma = cellSignalStrengthTdscdma != null
                ? cellSignalStrengthTdscdma : new CellSignalStrengthTdscdma();
    }

    private CellInfoTdscdma(Parcel in) {
        super(in);
        mCellIdentityTdscdma = CellIdentityTdscdma.CREATOR.createFromParcel(in);
        mCellSignalStrengthTdscdma = CellSignalStrengthTdscdma.CREATOR.createFromParcel(in);
    }

    public CellIdentityTdscdma getCellIdentity() {
        return mCellIdentityTdscdma;
    }

    public CellSignalStrengthTdscdma getCellSignalStrength() {
        return mCellSignalStrengthTdscdma;
    }

    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(super.hashCode()),
                mCellIdentityTdscdma,
                mCellSignalStrengthTdscdma);
    }

    public boolean equals(Object other) {
        if (!super.equals(other)) {
            return false;
        }
        if (!(other instanceof CellInfoTdscdma)) {
            return false;
        }
        CellInfoTdscdma o = (CellInfoTdscdma) other;
        return Objects.equals(mCellIdentityTdscdma, o.mCellIdentityTdscdma)
                && Objects.equals(mCellSignalStrengthTdscdma, o.mCellSignalStrengthTdscdma);
    }

    public String toString() {
        return "CellInfoTdscdma:{" + super.toString()
                + " " + mCellIdentityTdscdma
                + " " + mCellSignalStrengthTdscdma + "}";
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        writeToParcelHeader(dest, flags, TYPE_TDSCDMA);
        mCellIdentityTdscdma.writeToParcel(dest, flags);
        mCellSignalStrengthTdscdma.writeToParcel(dest, flags);
    }

    static CellInfoTdscdma createFromParcelBody(Parcel in) {
        return new CellInfoTdscdma(in);
    }

    public static final Parcelable.Creator<CellInfoTdscdma> CREATOR =
            new Parcelable.Creator<CellInfoTdscdma>() {
                public CellInfoTdscdma createFromParcel(Parcel in) {
                    in.readInt();
                    return createFromParcelBody(in);
                }

                public CellInfoTdscdma[] newArray(int size) {
                    return new CellInfoTdscdma[size];
                }
            };
}
