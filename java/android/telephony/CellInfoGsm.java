// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class CellInfoGsm extends CellInfo implements Parcelable {
    private CellIdentityGsm mCellIdentityGsm;
    private CellSignalStrengthGsm mCellSignalStrengthGsm;

    CellInfoGsm() {
        super();
        mCellIdentityGsm = new CellIdentityGsm();
        mCellSignalStrengthGsm = new CellSignalStrengthGsm();
    }

    CellInfoGsm(int connectionStatus, boolean registered, long timeStampNanos,
            CellIdentityGsm cellIdentityGsm, CellSignalStrengthGsm cellSignalStrengthGsm) {
        super(connectionStatus, registered, timeStampNanos);
        mCellIdentityGsm = cellIdentityGsm != null ? cellIdentityGsm : new CellIdentityGsm();
        mCellSignalStrengthGsm = cellSignalStrengthGsm != null
                ? cellSignalStrengthGsm : new CellSignalStrengthGsm();
    }

    private CellInfoGsm(Parcel in) {
        super(in);
        mCellIdentityGsm = CellIdentityGsm.CREATOR.createFromParcel(in);
        mCellSignalStrengthGsm = CellSignalStrengthGsm.CREATOR.createFromParcel(in);
    }

    public CellIdentityGsm getCellIdentity() {
        return mCellIdentityGsm;
    }

    public CellSignalStrengthGsm getCellSignalStrength() {
        return mCellSignalStrengthGsm;
    }

    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(super.hashCode()),
                mCellIdentityGsm,
                mCellSignalStrengthGsm);
    }

    public boolean equals(Object other) {
        if (!super.equals(other)) {
            return false;
        }
        if (!(other instanceof CellInfoGsm)) {
            return false;
        }
        CellInfoGsm o = (CellInfoGsm) other;
        return Objects.equals(mCellIdentityGsm, o.mCellIdentityGsm)
                && Objects.equals(mCellSignalStrengthGsm, o.mCellSignalStrengthGsm);
    }

    public String toString() {
        return "CellInfoGsm:{" + super.toString()
                + " " + mCellIdentityGsm
                + " " + mCellSignalStrengthGsm + "}";
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        writeToParcelHeader(dest, flags, TYPE_GSM);
        mCellIdentityGsm.writeToParcel(dest, flags);
        mCellSignalStrengthGsm.writeToParcel(dest, flags);
    }

    static CellInfoGsm createFromParcelBody(Parcel in) {
        return new CellInfoGsm(in);
    }

    public static final Parcelable.Creator<CellInfoGsm> CREATOR =
            new Parcelable.Creator<CellInfoGsm>() {
                public CellInfoGsm createFromParcel(Parcel in) {
                    in.readInt();
                    return createFromParcelBody(in);
                }

                public CellInfoGsm[] newArray(int size) {
                    return new CellInfoGsm[size];
                }
            };
}
