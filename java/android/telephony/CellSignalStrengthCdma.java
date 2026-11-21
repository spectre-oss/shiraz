// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class CellSignalStrengthCdma extends CellSignalStrength implements Parcelable {
    private int mCdmaDbm;
    private int mCdmaEcio;
    private int mEvdoDbm;
    private int mEvdoEcio;
    private int mEvdoSnr;
    private int mLevel;

    CellSignalStrengthCdma() {
        mCdmaDbm = CellInfo.UNAVAILABLE;
        mCdmaEcio = CellInfo.UNAVAILABLE;
        mEvdoDbm = CellInfo.UNAVAILABLE;
        mEvdoEcio = CellInfo.UNAVAILABLE;
        mEvdoSnr = CellInfo.UNAVAILABLE;
        mLevel = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
    }

    CellSignalStrengthCdma(int cdmaDbm, int cdmaEcio, int evdoDbm, int evdoEcio, int evdoSnr) {
        mCdmaDbm = normalizeNegative(cdmaDbm, -120, -40);
        mCdmaEcio = normalizeNegative(cdmaEcio, -160, 0);
        mEvdoDbm = normalizeNegative(evdoDbm, -120, -40);
        mEvdoEcio = normalizeNegative(evdoEcio, -160, 0);
        mEvdoSnr = (evdoSnr >= 0 && evdoSnr <= 8) ? evdoSnr : CellInfo.UNAVAILABLE;
        updateLevel();
    }

    private CellSignalStrengthCdma(Parcel in) {
        mCdmaDbm = in.readInt();
        mCdmaEcio = in.readInt();
        mEvdoDbm = in.readInt();
        mEvdoEcio = in.readInt();
        mEvdoSnr = in.readInt();
        mLevel = in.readInt();
    }

    private static int normalizeNegative(int value, int min, int max) {
        if (value == CellInfo.UNAVAILABLE) {
            return CellInfo.UNAVAILABLE;
        }
        int neg = value > 0 ? -value : value;
        if (neg < min || neg > max) {
            return CellInfo.UNAVAILABLE;
        }
        return neg;
    }

    void updateLevel() {
        int cdmaLevel = getCdmaLevel();
        int evdoLevel = getEvdoLevel();
        if (evdoLevel == SIGNAL_STRENGTH_NONE_OR_UNKNOWN) {
            mLevel = cdmaLevel;
        } else if (cdmaLevel == SIGNAL_STRENGTH_NONE_OR_UNKNOWN) {
            mLevel = evdoLevel;
        } else {
            mLevel = cdmaLevel > evdoLevel ? cdmaLevel : evdoLevel;
        }
    }

    public int getLevel() {
        return mLevel;
    }

    public int getAsuLevel() {
        final int cdmaDbm = getCdmaDbm();
        final int cdmaEcio = getCdmaEcio();
        int cdmaAsuLevel;
        int ecioAsuLevel;

        if (cdmaDbm == CellInfo.UNAVAILABLE) {
            cdmaAsuLevel = 99;
        } else if (cdmaDbm >= -75) {
            cdmaAsuLevel = 16;
        } else if (cdmaDbm >= -82) {
            cdmaAsuLevel = 8;
        } else if (cdmaDbm >= -90) {
            cdmaAsuLevel = 4;
        } else if (cdmaDbm >= -95) {
            cdmaAsuLevel = 2;
        } else if (cdmaDbm >= -100) {
            cdmaAsuLevel = 1;
        } else {
            cdmaAsuLevel = 99;
        }

        if (cdmaEcio == CellInfo.UNAVAILABLE) {
            ecioAsuLevel = 99;
        } else if (cdmaEcio >= -90) {
            ecioAsuLevel = 16;
        } else if (cdmaEcio >= -100) {
            ecioAsuLevel = 8;
        } else if (cdmaEcio >= -115) {
            ecioAsuLevel = 4;
        } else if (cdmaEcio >= -130) {
            ecioAsuLevel = 2;
        } else if (cdmaEcio >= -150) {
            ecioAsuLevel = 1;
        } else {
            ecioAsuLevel = 99;
        }

        return cdmaAsuLevel < ecioAsuLevel ? cdmaAsuLevel : ecioAsuLevel;
    }

    public int getCdmaLevel() {
        final int cdmaDbm = getCdmaDbm();
        final int cdmaEcio = getCdmaEcio();
        int levelDbm;
        int levelEcio;

        if (cdmaDbm == CellInfo.UNAVAILABLE) {
            levelDbm = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
        } else if (cdmaDbm >= -75) {
            levelDbm = SIGNAL_STRENGTH_GREAT;
        } else if (cdmaDbm >= -85) {
            levelDbm = SIGNAL_STRENGTH_GOOD;
        } else if (cdmaDbm >= -95) {
            levelDbm = SIGNAL_STRENGTH_MODERATE;
        } else if (cdmaDbm >= -100) {
            levelDbm = SIGNAL_STRENGTH_POOR;
        } else {
            levelDbm = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
        }

        if (cdmaEcio == CellInfo.UNAVAILABLE) {
            levelEcio = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
        } else if (cdmaEcio >= -90) {
            levelEcio = SIGNAL_STRENGTH_GREAT;
        } else if (cdmaEcio >= -110) {
            levelEcio = SIGNAL_STRENGTH_GOOD;
        } else if (cdmaEcio >= -130) {
            levelEcio = SIGNAL_STRENGTH_MODERATE;
        } else if (cdmaEcio >= -150) {
            levelEcio = SIGNAL_STRENGTH_POOR;
        } else {
            levelEcio = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
        }

        if (cdmaEcio == CellInfo.UNAVAILABLE) {
            return levelDbm;
        }
        if (cdmaDbm == CellInfo.UNAVAILABLE) {
            return levelEcio;
        }
        return levelDbm < levelEcio ? levelDbm : levelEcio;
    }

    public int getEvdoLevel() {
        int evdoDbm = getEvdoDbm();
        int evdoSnr = getEvdoSnr();
        int levelEvdoDbm;
        int levelEvdoSnr;

        if (evdoDbm == CellInfo.UNAVAILABLE) {
            levelEvdoDbm = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
        } else if (evdoDbm >= -65) {
            levelEvdoDbm = SIGNAL_STRENGTH_GREAT;
        } else if (evdoDbm >= -75) {
            levelEvdoDbm = SIGNAL_STRENGTH_GOOD;
        } else if (evdoDbm >= -90) {
            levelEvdoDbm = SIGNAL_STRENGTH_MODERATE;
        } else if (evdoDbm >= -105) {
            levelEvdoDbm = SIGNAL_STRENGTH_POOR;
        } else {
            levelEvdoDbm = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
        }

        if (evdoSnr == CellInfo.UNAVAILABLE) {
            levelEvdoSnr = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
        } else if (evdoSnr >= 7) {
            levelEvdoSnr = SIGNAL_STRENGTH_GREAT;
        } else if (evdoSnr >= 5) {
            levelEvdoSnr = SIGNAL_STRENGTH_GOOD;
        } else if (evdoSnr >= 3) {
            levelEvdoSnr = SIGNAL_STRENGTH_MODERATE;
        } else if (evdoSnr >= 1) {
            levelEvdoSnr = SIGNAL_STRENGTH_POOR;
        } else {
            levelEvdoSnr = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
        }

        if (evdoSnr == CellInfo.UNAVAILABLE) {
            return levelEvdoDbm;
        }
        if (evdoDbm == CellInfo.UNAVAILABLE) {
            return levelEvdoSnr;
        }
        return levelEvdoDbm < levelEvdoSnr ? levelEvdoDbm : levelEvdoSnr;
    }

    public int getDbm() {
        int cdmaDbm = getCdmaDbm();
        int evdoDbm = getEvdoDbm();
        return cdmaDbm < evdoDbm ? cdmaDbm : evdoDbm;
    }

    public int getCdmaDbm() {
        return mCdmaDbm;
    }

    public int getCdmaEcio() {
        return mCdmaEcio;
    }

    public int getEvdoDbm() {
        return mEvdoDbm;
    }

    public int getEvdoEcio() {
        return mEvdoEcio;
    }

    public int getEvdoSnr() {
        return mEvdoSnr;
    }

    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(mCdmaDbm),
                Integer.valueOf(mCdmaEcio),
                Integer.valueOf(mEvdoDbm),
                Integer.valueOf(mEvdoEcio),
                Integer.valueOf(mEvdoSnr),
                Integer.valueOf(mLevel));
    }

    public boolean equals(Object o) {
        if (!(o instanceof CellSignalStrengthCdma)) {
            return false;
        }
        CellSignalStrengthCdma s = (CellSignalStrengthCdma) o;
        return mCdmaDbm == s.mCdmaDbm
                && mCdmaEcio == s.mCdmaEcio
                && mEvdoDbm == s.mEvdoDbm
                && mEvdoEcio == s.mEvdoEcio
                && mEvdoSnr == s.mEvdoSnr
                && mLevel == s.mLevel;
    }

    public String toString() {
        return "CellSignalStrengthCdma: cdmaDbm=" + mCdmaDbm
                + " cdmaEcio=" + mCdmaEcio
                + " evdoDbm=" + mEvdoDbm
                + " evdoEcio=" + mEvdoEcio
                + " evdoSnr=" + mEvdoSnr
                + " level=" + mLevel;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mCdmaDbm);
        dest.writeInt(mCdmaEcio);
        dest.writeInt(mEvdoDbm);
        dest.writeInt(mEvdoEcio);
        dest.writeInt(mEvdoSnr);
        dest.writeInt(mLevel);
    }

    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<CellSignalStrengthCdma> CREATOR =
            new Parcelable.Creator<CellSignalStrengthCdma>() {
                public CellSignalStrengthCdma createFromParcel(Parcel in) {
                    return new CellSignalStrengthCdma(in);
                }

                public CellSignalStrengthCdma[] newArray(int size) {
                    return new CellSignalStrengthCdma[size];
                }
            };
}
