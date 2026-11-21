// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class CellInfoWcdma extends CellInfo implements Parcelable {
    private CellIdentityWcdma mCellIdentityWcdma;
    private CellSignalStrengthWcdma mCellSignalStrengthWcdma;

    CellInfoWcdma() {
        super();
        mCellIdentityWcdma = new CellIdentityWcdma();
        mCellSignalStrengthWcdma = new CellSignalStrengthWcdma();
    }

    CellInfoWcdma(int connectionStatus, boolean registered, long timeStampNanos,
            CellIdentityWcdma cellIdentityWcdma,
            CellSignalStrengthWcdma cellSignalStrengthWcdma) {
        super(connectionStatus, registered, timeStampNanos);
        mCellIdentityWcdma = cellIdentityWcdma != null
                ? cellIdentityWcdma : new CellIdentityWcdma();
        mCellSignalStrengthWcdma = cellSignalStrengthWcdma != null
                ? cellSignalStrengthWcdma : new CellSignalStrengthWcdma();
    }

    private CellInfoWcdma(Parcel in) {
        super(in);
        mCellIdentityWcdma = CellIdentityWcdma.CREATOR.createFromParcel(in);
        mCellSignalStrengthWcdma = CellSignalStrengthWcdma.CREATOR.createFromParcel(in);
    }

    public CellIdentityWcdma getCellIdentity() {
        return mCellIdentityWcdma;
    }

    public CellSignalStrengthWcdma getCellSignalStrength() {
        return mCellSignalStrengthWcdma;
    }

    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(super.hashCode()),
                mCellIdentityWcdma,
                mCellSignalStrengthWcdma);
    }

    public boolean equals(Object other) {
        if (!super.equals(other)) {
            return false;
        }
        if (!(other instanceof CellInfoWcdma)) {
            return false;
        }
        CellInfoWcdma o = (CellInfoWcdma) other;
        return Objects.equals(mCellIdentityWcdma, o.mCellIdentityWcdma)
                && Objects.equals(mCellSignalStrengthWcdma, o.mCellSignalStrengthWcdma);
    }

    public String toString() {
        return "CellInfoWcdma:{" + super.toString()
                + " " + mCellIdentityWcdma
                + " " + mCellSignalStrengthWcdma + "}";
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        writeToParcelHeader(dest, flags, TYPE_WCDMA);
        mCellIdentityWcdma.writeToParcel(dest, flags);
        mCellSignalStrengthWcdma.writeToParcel(dest, flags);
    }

    static CellInfoWcdma createFromParcelBody(Parcel in) {
        return new CellInfoWcdma(in);
    }

    public static final Parcelable.Creator<CellInfoWcdma> CREATOR =
            new Parcelable.Creator<CellInfoWcdma>() {
                public CellInfoWcdma createFromParcel(Parcel in) {
                    in.readInt();
                    return createFromParcelBody(in);
                }

                public CellInfoWcdma[] newArray(int size) {
                    return new CellInfoWcdma[size];
                }
            };
}
