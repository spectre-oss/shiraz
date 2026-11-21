// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class CellSignalStrengthGsm extends CellSignalStrength implements Parcelable {
    private int mRssi;
    private int mBitErrorRate;
    private int mTimingAdvance;
    private int mLevel;

    CellSignalStrengthGsm() {
        mRssi = CellInfo.UNAVAILABLE;
        mBitErrorRate = CellInfo.UNAVAILABLE;
        mTimingAdvance = CellInfo.UNAVAILABLE;
        mLevel = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
    }

    CellSignalStrengthGsm(int rssi, int ber, int ta) {
        mRssi = (rssi >= -113 && rssi <= -51) ? rssi : CellInfo.UNAVAILABLE;
        mBitErrorRate = (ber >= 0 && ber <= 7) ? ber : CellInfo.UNAVAILABLE;
        mTimingAdvance = (ta >= 0 && ta <= 219) ? ta : CellInfo.UNAVAILABLE;
        updateLevel();
    }

    private CellSignalStrengthGsm(Parcel in) {
        mRssi = in.readInt();
        mBitErrorRate = in.readInt();
        mTimingAdvance = in.readInt();
        mLevel = in.readInt();
    }

    void updateLevel() {
        if (mRssi < -113 || mRssi > -51) {
            mLevel = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
        } else if (mRssi >= -89) {
            mLevel = SIGNAL_STRENGTH_GREAT;
        } else if (mRssi >= -97) {
            mLevel = SIGNAL_STRENGTH_GOOD;
        } else if (mRssi >= -103) {
            mLevel = SIGNAL_STRENGTH_MODERATE;
        } else if (mRssi >= -107) {
            mLevel = SIGNAL_STRENGTH_POOR;
        } else {
            mLevel = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
        }
    }

    public int getLevel() {
        return mLevel;
    }

    public int getTimingAdvance() {
        return mTimingAdvance;
    }

    public int getDbm() {
        return mRssi;
    }

    public int getAsuLevel() {
        if (mRssi < -113 || mRssi > -51) {
            return 99;
        }
        return (mRssi + 113) / 2;
    }

    public int getRssi() {
        return mRssi;
    }

    public int getBitErrorRate() {
        return mBitErrorRate;
    }

    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(mRssi),
                Integer.valueOf(mBitErrorRate),
                Integer.valueOf(mTimingAdvance),
                Integer.valueOf(mLevel));
    }

    public boolean equals(Object o) {
        if (!(o instanceof CellSignalStrengthGsm)) {
            return false;
        }
        CellSignalStrengthGsm s = (CellSignalStrengthGsm) o;
        return mRssi == s.mRssi
                && mBitErrorRate == s.mBitErrorRate
                && mTimingAdvance == s.mTimingAdvance
                && mLevel == s.mLevel;
    }

    public String toString() {
        return "CellSignalStrengthGsm: rssi=" + mRssi
                + " ber=" + mBitErrorRate
                + " mTa=" + mTimingAdvance
                + " mLevel=" + mLevel;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mRssi);
        dest.writeInt(mBitErrorRate);
        dest.writeInt(mTimingAdvance);
        dest.writeInt(mLevel);
    }

    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<CellSignalStrengthGsm> CREATOR =
            new Parcelable.Creator<CellSignalStrengthGsm>() {
                public CellSignalStrengthGsm createFromParcel(Parcel in) {
                    return new CellSignalStrengthGsm(in);
                }

                public CellSignalStrengthGsm[] newArray(int size) {
                    return new CellSignalStrengthGsm[size];
                }
            };
}
