// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class CellSignalStrengthWcdma extends CellSignalStrength implements Parcelable {
    private int mRssi;
    private int mBitErrorRate;
    private int mRscp;
    private int mEcNo;
    private int mLevel;

    CellSignalStrengthWcdma() {
        mRssi = CellInfo.UNAVAILABLE;
        mBitErrorRate = CellInfo.UNAVAILABLE;
        mRscp = CellInfo.UNAVAILABLE;
        mEcNo = CellInfo.UNAVAILABLE;
        mLevel = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
    }

    CellSignalStrengthWcdma(int rssi, int ber, int rscp, int ecno) {
        mRssi = (rssi >= -113 && rssi <= -51) ? rssi : CellInfo.UNAVAILABLE;
        mBitErrorRate = (ber >= 0 && ber <= 7) ? ber : CellInfo.UNAVAILABLE;
        mRscp = (rscp >= -120 && rscp <= -24) ? rscp : CellInfo.UNAVAILABLE;
        mEcNo = (ecno >= -24 && ecno <= 1) ? ecno : CellInfo.UNAVAILABLE;
        updateLevel();
    }

    private CellSignalStrengthWcdma(Parcel in) {
        mRssi = in.readInt();
        mBitErrorRate = in.readInt();
        mRscp = in.readInt();
        mEcNo = in.readInt();
        mLevel = in.readInt();
    }

    void updateLevel() {
        if (mRscp != CellInfo.UNAVAILABLE) {
            if (mRscp >= -85) {
                mLevel = SIGNAL_STRENGTH_GREAT;
            } else if (mRscp >= -95) {
                mLevel = SIGNAL_STRENGTH_GOOD;
            } else if (mRscp >= -105) {
                mLevel = SIGNAL_STRENGTH_MODERATE;
            } else if (mRscp >= -115) {
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

    public int getDbm() {
        if (mRscp != CellInfo.UNAVAILABLE) {
            return mRscp;
        }
        return mRssi;
    }

    public int getAsuLevel() {
        if (mRscp != CellInfo.UNAVAILABLE) {
            if (mRscp <= -120) {
                return 0;
            }
            if (mRscp >= -24) {
                return 96;
            }
            return mRscp + 120;
        }
        if (mRssi != CellInfo.UNAVAILABLE) {
            return (mRssi + 113) / 2;
        }
        return 99;
    }

    public int getEcNo() {
        return mEcNo;
    }

    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(mRssi),
                Integer.valueOf(mBitErrorRate),
                Integer.valueOf(mRscp),
                Integer.valueOf(mEcNo),
                Integer.valueOf(mLevel));
    }

    public boolean equals(Object o) {
        if (!(o instanceof CellSignalStrengthWcdma)) {
            return false;
        }
        CellSignalStrengthWcdma s = (CellSignalStrengthWcdma) o;
        return mRssi == s.mRssi
                && mBitErrorRate == s.mBitErrorRate
                && mRscp == s.mRscp
                && mEcNo == s.mEcNo
                && mLevel == s.mLevel;
    }

    public String toString() {
        return "CellSignalStrengthWcdma: ss=" + mRssi
                + " ber=" + mBitErrorRate
                + " rscp=" + mRscp
                + " ecno=" + mEcNo
                + " level=" + mLevel;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mRssi);
        dest.writeInt(mBitErrorRate);
        dest.writeInt(mRscp);
        dest.writeInt(mEcNo);
        dest.writeInt(mLevel);
    }

    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<CellSignalStrengthWcdma> CREATOR =
            new Parcelable.Creator<CellSignalStrengthWcdma>() {
                public CellSignalStrengthWcdma createFromParcel(Parcel in) {
                    return new CellSignalStrengthWcdma(in);
                }

                public CellSignalStrengthWcdma[] newArray(int size) {
                    return new CellSignalStrengthWcdma[size];
                }
            };
}
