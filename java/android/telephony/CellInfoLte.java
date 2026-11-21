// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class CellInfoLte extends CellInfo implements Parcelable {
    private CellIdentityLte mCellIdentityLte;
    private CellSignalStrengthLte mCellSignalStrengthLte;

    CellInfoLte() {
        super();
        mCellIdentityLte = new CellIdentityLte();
        mCellSignalStrengthLte = new CellSignalStrengthLte();
    }

    CellInfoLte(int connectionStatus, boolean registered, long timeStampNanos,
            CellIdentityLte cellIdentityLte, CellSignalStrengthLte cellSignalStrengthLte) {
        super(connectionStatus, registered, timeStampNanos);
        mCellIdentityLte = cellIdentityLte != null ? cellIdentityLte : new CellIdentityLte();
        mCellSignalStrengthLte = cellSignalStrengthLte != null
                ? cellSignalStrengthLte : new CellSignalStrengthLte();
    }

    private CellInfoLte(Parcel in) {
        super(in);
        mCellIdentityLte = CellIdentityLte.CREATOR.createFromParcel(in);
        mCellSignalStrengthLte = CellSignalStrengthLte.CREATOR.createFromParcel(in);
    }

    public CellIdentityLte getCellIdentity() {
        return mCellIdentityLte;
    }

    public CellSignalStrengthLte getCellSignalStrength() {
        return mCellSignalStrengthLte;
    }

    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(super.hashCode()),
                mCellIdentityLte,
                mCellSignalStrengthLte);
    }

    public boolean equals(Object other) {
        if (!super.equals(other)) {
            return false;
        }
        if (!(other instanceof CellInfoLte)) {
            return false;
        }
        CellInfoLte o = (CellInfoLte) other;
        return Objects.equals(mCellIdentityLte, o.mCellIdentityLte)
                && Objects.equals(mCellSignalStrengthLte, o.mCellSignalStrengthLte);
    }

    public String toString() {
        return "CellInfoLte:{" + super.toString()
                + " " + mCellIdentityLte
                + " " + mCellSignalStrengthLte + "}";
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        writeToParcelHeader(dest, flags, TYPE_LTE);
        mCellIdentityLte.writeToParcel(dest, flags);
        mCellSignalStrengthLte.writeToParcel(dest, flags);
    }

    static CellInfoLte createFromParcelBody(Parcel in) {
        return new CellInfoLte(in);
    }

    public static final Parcelable.Creator<CellInfoLte> CREATOR =
            new Parcelable.Creator<CellInfoLte>() {
                public CellInfoLte createFromParcel(Parcel in) {
                    in.readInt();
                    return createFromParcelBody(in);
                }

                public CellInfoLte[] newArray(int size) {
                    return new CellInfoLte[size];
                }
            };
}
