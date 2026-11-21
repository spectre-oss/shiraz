// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class CellSignalStrengthNr extends CellSignalStrength implements Parcelable {
    private int mCsiRsrp;
    private int mCsiRsrq;
    private int mCsiSinr;
    private int mCsiCqiTableIndex;
    private List<Integer> mCsiCqiReport;
    private int mSsRsrp;
    private int mSsRsrq;
    private int mSsSinr;
    private int mLevel;
    private int mTimingAdvance;

    CellSignalStrengthNr() {
        mCsiRsrp = CellInfo.UNAVAILABLE;
        mCsiRsrq = CellInfo.UNAVAILABLE;
        mCsiSinr = CellInfo.UNAVAILABLE;
        mCsiCqiTableIndex = CellInfo.UNAVAILABLE;
        mCsiCqiReport = Collections.emptyList();
        mSsRsrp = CellInfo.UNAVAILABLE;
        mSsRsrq = CellInfo.UNAVAILABLE;
        mSsSinr = CellInfo.UNAVAILABLE;
        mLevel = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
        mTimingAdvance = CellInfo.UNAVAILABLE;
    }

    CellSignalStrengthNr(int csiRsrp, int csiRsrq, int csiSinr, int csiCqiTableIndex,
            List<Integer> csiCqiReport, int ssRsrp, int ssRsrq, int ssSinr, int timingAdvance) {
        mCsiRsrp = (csiRsrp >= -156 && csiRsrp <= -31) ? csiRsrp : CellInfo.UNAVAILABLE;
        mCsiRsrq = (csiRsrq >= -43 && csiRsrq <= 20) ? csiRsrq : CellInfo.UNAVAILABLE;
        mCsiSinr = (csiSinr >= -23 && csiSinr <= 40) ? csiSinr : CellInfo.UNAVAILABLE;
        mCsiCqiTableIndex = (csiCqiTableIndex >= 1 && csiCqiTableIndex <= 3)
                ? csiCqiTableIndex : CellInfo.UNAVAILABLE;
        mCsiCqiReport = csiCqiReport == null
                ? Collections.<Integer>emptyList()
                : Collections.unmodifiableList(new ArrayList<Integer>(csiCqiReport));
        mSsRsrp = (ssRsrp >= -156 && ssRsrp <= -31) ? ssRsrp : CellInfo.UNAVAILABLE;
        mSsRsrq = (ssRsrq >= -43 && ssRsrq <= 20) ? ssRsrq : CellInfo.UNAVAILABLE;
        mSsSinr = (ssSinr >= -23 && ssSinr <= 40) ? ssSinr : CellInfo.UNAVAILABLE;
        mTimingAdvance = (timingAdvance >= 0 && timingAdvance <= 1282)
                ? timingAdvance : CellInfo.UNAVAILABLE;
        updateLevel();
    }

    private CellSignalStrengthNr(Parcel in) {
        mCsiRsrp = in.readInt();
        mCsiRsrq = in.readInt();
        mCsiSinr = in.readInt();
        mCsiCqiTableIndex = in.readInt();
        int reportSize = in.readInt();
        if (reportSize <= 0) {
            mCsiCqiReport = Collections.emptyList();
        } else {
            ArrayList<Integer> list = new ArrayList<Integer>(reportSize);
            for (int i = 0; i < reportSize; i++) {
                list.add(Integer.valueOf(in.readInt()));
            }
            mCsiCqiReport = Collections.unmodifiableList(list);
        }
        mSsRsrp = in.readInt();
        mSsRsrq = in.readInt();
        mSsSinr = in.readInt();
        mLevel = in.readInt();
        mTimingAdvance = in.readInt();
    }

    void updateLevel() {
        if (mSsRsrp == CellInfo.UNAVAILABLE) {
            mLevel = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
        } else if (mSsRsrp >= -95) {
            mLevel = SIGNAL_STRENGTH_GREAT;
        } else if (mSsRsrp >= -105) {
            mLevel = SIGNAL_STRENGTH_GOOD;
        } else if (mSsRsrp >= -115) {
            mLevel = SIGNAL_STRENGTH_MODERATE;
        } else if (mSsRsrp >= -125) {
            mLevel = SIGNAL_STRENGTH_POOR;
        } else {
            mLevel = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
        }
    }

    public int getSsRsrp() {
        return mSsRsrp;
    }

    public int getSsRsrq() {
        return mSsRsrq;
    }

    public int getSsSinr() {
        return mSsSinr;
    }

    public int getCsiRsrp() {
        return mCsiRsrp;
    }

    public int getCsiRsrq() {
        return mCsiRsrq;
    }

    public int getCsiSinr() {
        return mCsiSinr;
    }

    public int getCsiCqiTableIndex() {
        return mCsiCqiTableIndex;
    }

    public List<Integer> getCsiCqiReport() {
        return mCsiCqiReport;
    }

    public int getTimingAdvanceMicros() {
        return mTimingAdvance;
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mCsiRsrp);
        dest.writeInt(mCsiRsrq);
        dest.writeInt(mCsiSinr);
        dest.writeInt(mCsiCqiTableIndex);
        dest.writeInt(mCsiCqiReport.size());
        for (int i = 0; i < mCsiCqiReport.size(); i++) {
            dest.writeInt(mCsiCqiReport.get(i).intValue());
        }
        dest.writeInt(mSsRsrp);
        dest.writeInt(mSsRsrq);
        dest.writeInt(mSsSinr);
        dest.writeInt(mLevel);
        dest.writeInt(mTimingAdvance);
    }

    public int getLevel() {
        return mLevel;
    }

    public int getAsuLevel() {
        if (mSsRsrp == CellInfo.UNAVAILABLE) {
            return 99;
        }
        if (mSsRsrp <= -140) {
            return 0;
        }
        if (mSsRsrp >= -44) {
            return 96;
        }
        return mSsRsrp + 140;
    }

    public int getDbm() {
        return mSsRsrp;
    }

    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(mCsiRsrp),
                Integer.valueOf(mCsiRsrq),
                Integer.valueOf(mCsiSinr),
                Integer.valueOf(mCsiCqiTableIndex),
                mCsiCqiReport,
                Integer.valueOf(mSsRsrp),
                Integer.valueOf(mSsRsrq),
                Integer.valueOf(mSsSinr),
                Integer.valueOf(mLevel),
                Integer.valueOf(mTimingAdvance));
    }

    public boolean equals(Object obj) {
        if (!(obj instanceof CellSignalStrengthNr)) {
            return false;
        }
        CellSignalStrengthNr s = (CellSignalStrengthNr) obj;
        return mCsiRsrp == s.mCsiRsrp
                && mCsiRsrq == s.mCsiRsrq
                && mCsiSinr == s.mCsiSinr
                && mCsiCqiTableIndex == s.mCsiCqiTableIndex
                && Objects.equals(mCsiCqiReport, s.mCsiCqiReport)
                && mSsRsrp == s.mSsRsrp
                && mSsRsrq == s.mSsRsrq
                && mSsSinr == s.mSsSinr
                && mLevel == s.mLevel
                && mTimingAdvance == s.mTimingAdvance;
    }

    public String toString() {
        return "CellSignalStrengthNr:{"
                + " csiRsrp = " + mCsiRsrp
                + " csiRsrq = " + mCsiRsrq
                + " csiSinr = " + mCsiSinr
                + " csiCqiTableIndex = " + mCsiCqiTableIndex
                + " csiCqiReport = " + mCsiCqiReport
                + " ssRsrp = " + mSsRsrp
                + " ssRsrq = " + mSsRsrq
                + " ssSinr = " + mSsSinr
                + " level = " + mLevel
                + " timingAdvance = " + mTimingAdvance
                + " }";
    }

    public static final Parcelable.Creator<CellSignalStrengthNr> CREATOR =
            new Parcelable.Creator<CellSignalStrengthNr>() {
                public CellSignalStrengthNr createFromParcel(Parcel in) {
                    return new CellSignalStrengthNr(in);
                }

                public CellSignalStrengthNr[] newArray(int size) {
                    return new CellSignalStrengthNr[size];
                }
            };
}
