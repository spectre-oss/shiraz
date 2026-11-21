// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class CellInfoNr extends CellInfo {
    private final CellIdentityNr mCellIdentity;
    private final CellSignalStrengthNr mCellSignalStrength;

    CellInfoNr() {
        super();
        mCellIdentity = new CellIdentityNr();
        mCellSignalStrength = new CellSignalStrengthNr();
    }

    CellInfoNr(int connectionStatus, boolean registered, long timeStampNanos,
            CellIdentityNr cellIdentityNr, CellSignalStrengthNr cellSignalStrengthNr) {
        super(connectionStatus, registered, timeStampNanos);
        mCellIdentity = cellIdentityNr != null ? cellIdentityNr : new CellIdentityNr();
        mCellSignalStrength = cellSignalStrengthNr != null
                ? cellSignalStrengthNr : new CellSignalStrengthNr();
    }

    private CellInfoNr(Parcel in) {
        super(in);
        mCellIdentity = CellIdentityNr.CREATOR.createFromParcel(in);
        mCellSignalStrength = CellSignalStrengthNr.CREATOR.createFromParcel(in);
    }

    public CellIdentity getCellIdentity() {
        return mCellIdentity;
    }

    public CellSignalStrength getCellSignalStrength() {
        return mCellSignalStrength;
    }

    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(super.hashCode()),
                mCellIdentity,
                mCellSignalStrength);
    }

    public boolean equals(Object other) {
        if (!(other instanceof CellInfoNr)) {
            return false;
        }
        CellInfoNr o = (CellInfoNr) other;
        return super.equals(o)
                && Objects.equals(mCellIdentity, o.mCellIdentity)
                && Objects.equals(mCellSignalStrength, o.mCellSignalStrength);
    }

    public String toString() {
        return "CellInfoNr:{" + " " + super.toString()
                + " " + mCellIdentity
                + " " + mCellSignalStrength + " }";
    }

    public void writeToParcel(Parcel dest, int flags) {
        writeToParcelHeader(dest, flags, TYPE_NR);
        mCellIdentity.writeToParcel(dest, flags);
        mCellSignalStrength.writeToParcel(dest, flags);
    }

    static CellInfoNr createFromParcelBody(Parcel in) {
        return new CellInfoNr(in);
    }

    public static final Parcelable.Creator<CellInfoNr> CREATOR =
            new Parcelable.Creator<CellInfoNr>() {
                public CellInfoNr createFromParcel(Parcel in) {
                    in.readInt();
                    return createFromParcelBody(in);
                }

                public CellInfoNr[] newArray(int size) {
                    return new CellInfoNr[size];
                }
            };
}
