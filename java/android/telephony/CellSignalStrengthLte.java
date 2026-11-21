// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class CellSignalStrengthLte extends CellSignalStrength implements Parcelable {
    private int mRssi;
    private int mRsrp;
    private int mRsrq;
    private int mRssnr;
    private int mCqiTableIndex;
    private int mCqi;
    private int mTimingAdvance;
    private int mLevel;

    CellSignalStrengthLte() {
        mRssi = CellInfo.UNAVAILABLE;
        mRsrp = CellInfo.UNAVAILABLE;
        mRsrq = CellInfo.UNAVAILABLE;
        mRssnr = CellInfo.UNAVAILABLE;
        mCqiTableIndex = CellInfo.UNAVAILABLE;
        mCqi = CellInfo.UNAVAILABLE;
        mTimingAdvance = CellInfo.UNAVAILABLE;
        mLevel = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
    }

    CellSignalStrengthLte(int rssi, int rsrp, int rsrq, int rssnr, int cqiTableIndex,
            int cqi, int timingAdvance) {
        mRssi = (rssi >= -113 && rssi <= -51) ? rssi : CellInfo.UNAVAILABLE;
        mRsrp = (rsrp >= -140 && rsrp <= -43) ? rsrp : CellInfo.UNAVAILABLE;
        mRsrq = (rsrq >= -34 && rsrq <= 3) ? rsrq : CellInfo.UNAVAILABLE;
        mRssnr = (rssnr >= -20 && rssnr <= 30) ? rssnr : CellInfo.UNAVAILABLE;
        mCqiTableIndex = (cqiTableIndex >= 0 && cqiTableIndex <= 3)
                ? cqiTableIndex : CellInfo.UNAVAILABLE;
        mCqi = (cqi >= 0 && cqi <= 15) ? cqi : CellInfo.UNAVAILABLE;
        mTimingAdvance = (timingAdvance >= 0 && timingAdvance <= 1282)
                ? timingAdvance : CellInfo.UNAVAILABLE;
        updateLevel();
    }

    private CellSignalStrengthLte(Parcel in) {
        mRssi = in.readInt();
        mRsrp = in.readInt();
        mRsrq = in.readInt();
        mRssnr = in.readInt();
        mCqiTableIndex = in.readInt();
        mCqi = in.readInt();
        mTimingAdvance = in.readInt();
        mLevel = in.readInt();
    }

    void updateLevel() {
        if (mRsrp != CellInfo.UNAVAILABLE) {
            if (mRsrp >= -98) {
                mLevel = SIGNAL_STRENGTH_GREAT;
            } else if (mRsrp >= -108) {
                mLevel = SIGNAL_STRENGTH_GOOD;
            } else if (mRsrp >= -118) {
                mLevel = SIGNAL_STRENGTH_MODERATE;
            } else if (mRsrp >= -128) {
                mLevel = SIGNAL_STRENGTH_POOR;
            } else {
                mLevel = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
            }
        } else if (mRssi != CellInfo.UNAVAILABLE) {
            if (mRssi >= -89) {
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
        } else {
            mLevel = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
        }
    }

    public int getLevel() {
        return mLevel;
    }

    public int getRsrq() {
        return mRsrq;
    }

    public int getRssi() {
        return mRssi;
    }

    public int getRssnr() {
        return mRssnr;
    }

    public int getRsrp() {
        return mRsrp;
    }

    public int getCqiTableIndex() {
        return mCqiTableIndex;
    }

    public int getCqi() {
        return mCqi;
    }

    public int getDbm() {
        return mRsrp;
    }

    public int getAsuLevel() {
        if (mRsrp == CellInfo.UNAVAILABLE) {
            return 99;
        }
        if (mRsrp <= -140) {
            return 0;
        }
        if (mRsrp >= -43) {
            return 97;
        }
        return mRsrp + 140;
    }

    public int getTimingAdvance() {
        return mTimingAdvance;
    }

    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(mRssi),
                Integer.valueOf(mRsrp),
                Integer.valueOf(mRsrq),
                Integer.valueOf(mRssnr),
                Integer.valueOf(mCqiTableIndex),
                Integer.valueOf(mCqi),
                Integer.valueOf(mTimingAdvance),
                Integer.valueOf(mLevel));
    }

    public boolean equals(Object o) {
        if (!(o instanceof CellSignalStrengthLte)) {
            return false;
        }
        CellSignalStrengthLte s = (CellSignalStrengthLte) o;
        return mRssi == s.mRssi
                && mRsrp == s.mRsrp
                && mRsrq == s.mRsrq
                && mRssnr == s.mRssnr
                && mCqiTableIndex == s.mCqiTableIndex
                && mCqi == s.mCqi
                && mTimingAdvance == s.mTimingAdvance
                && mLevel == s.mLevel;
    }

    public String toString() {
        return "CellSignalStrengthLte:"
                + " rssi=" + mRssi
                + " rsrp=" + mRsrp
                + " rsrq=" + mRsrq
                + " rssnr=" + mRssnr
                + " cqiTableIndex=" + mCqiTableIndex
                + " cqi=" + mCqi
                + " ta=" + mTimingAdvance
                + " level=" + mLevel;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mRssi);
        dest.writeInt(mRsrp);
        dest.writeInt(mRsrq);
        dest.writeInt(mRssnr);
        dest.writeInt(mCqiTableIndex);
        dest.writeInt(mCqi);
        dest.writeInt(mTimingAdvance);
        dest.writeInt(mLevel);
    }

    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<CellSignalStrengthLte> CREATOR =
            new Parcelable.Creator<CellSignalStrengthLte>() {
                public CellSignalStrengthLte createFromParcel(Parcel in) {
                    return new CellSignalStrengthLte(in);
                }

                public CellSignalStrengthLte[] newArray(int size) {
                    return new CellSignalStrengthLte[size];
                }
            };
}
