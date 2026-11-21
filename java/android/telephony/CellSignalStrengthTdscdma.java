// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class CellSignalStrengthTdscdma extends CellSignalStrength implements Parcelable {
    private int mRssi;
    private int mBitErrorRate;
    private int mRscp;
    private int mLevel;

    CellSignalStrengthTdscdma() {
        mRssi = CellInfo.UNAVAILABLE;
        mBitErrorRate = CellInfo.UNAVAILABLE;
        mRscp = CellInfo.UNAVAILABLE;
        mLevel = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
    }

    CellSignalStrengthTdscdma(int rssi, int ber, int rscp) {
        mRssi = (rssi >= -113 && rssi <= -51) ? rssi : CellInfo.UNAVAILABLE;
        mBitErrorRate = (ber >= 0 && ber <= 7) ? ber : CellInfo.UNAVAILABLE;
        mRscp = (rscp >= -120 && rscp <= -24) ? rscp : CellInfo.UNAVAILABLE;
        updateLevel();
    }

    private CellSignalStrengthTdscdma(Parcel in) {
        mRssi = in.readInt();
        mBitErrorRate = in.readInt();
        mRscp = in.readInt();
        mLevel = in.readInt();
    }

    void updateLevel() {
        if (mRscp == CellInfo.UNAVAILABLE) {
            mLevel = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
        } else if (mRscp >= -49) {
            mLevel = SIGNAL_STRENGTH_GREAT;
        } else if (mRscp >= -73) {
            mLevel = SIGNAL_STRENGTH_GOOD;
        } else if (mRscp >= -97) {
            mLevel = SIGNAL_STRENGTH_MODERATE;
        } else if (mRscp >= -110) {
            mLevel = SIGNAL_STRENGTH_POOR;
        } else {
            mLevel = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
        }
    }

    public int getLevel() {
        return mLevel;
    }

    public int getDbm() {
        return mRscp;
    }

    public int getRscp() {
        return mRscp;
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

    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(mRssi),
                Integer.valueOf(mBitErrorRate),
                Integer.valueOf(mRscp),
                Integer.valueOf(mLevel));
    }

    public boolean equals(Object o) {
        if (!(o instanceof CellSignalStrengthTdscdma)) {
            return false;
        }
        CellSignalStrengthTdscdma s = (CellSignalStrengthTdscdma) o;
        return mRssi == s.mRssi
                && mBitErrorRate == s.mBitErrorRate
                && mRscp == s.mRscp
                && mLevel == s.mLevel;
    }

    public String toString() {
        return "CellSignalStrengthTdscdma: rssi=" + mRssi
                + " ber=" + mBitErrorRate
                + " rscp=" + mRscp
                + " level=" + mLevel;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mRssi);
        dest.writeInt(mBitErrorRate);
        dest.writeInt(mRscp);
        dest.writeInt(mLevel);
    }

    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<CellSignalStrengthTdscdma> CREATOR =
            new Parcelable.Creator<CellSignalStrengthTdscdma>() {
                public CellSignalStrengthTdscdma createFromParcel(Parcel in) {
                    return new CellSignalStrengthTdscdma(in);
                }

                public CellSignalStrengthTdscdma[] newArray(int size) {
                    return new CellSignalStrengthTdscdma[size];
                }
            };
}
