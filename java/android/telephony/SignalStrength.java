// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SignalStrength implements Parcelable {
    public static final int INVALID = Integer.MAX_VALUE;

    private CellSignalStrengthCdma mCdma;
    private CellSignalStrengthGsm mGsm;
    private CellSignalStrengthWcdma mWcdma;
    private CellSignalStrengthTdscdma mTdscdma;
    private CellSignalStrengthLte mLte;
    private CellSignalStrengthNr mNr;
    private long mTimestampMillis;

    SignalStrength() {
        this(new CellSignalStrengthCdma(),
                new CellSignalStrengthGsm(),
                new CellSignalStrengthWcdma(),
                new CellSignalStrengthTdscdma(),
                new CellSignalStrengthLte(),
                new CellSignalStrengthNr());
    }

    SignalStrength(CellSignalStrengthCdma cdma, CellSignalStrengthGsm gsm,
            CellSignalStrengthWcdma wcdma, CellSignalStrengthTdscdma tdscdma,
            CellSignalStrengthLte lte, CellSignalStrengthNr nr) {
        mCdma = cdma != null ? cdma : new CellSignalStrengthCdma();
        mGsm = gsm != null ? gsm : new CellSignalStrengthGsm();
        mWcdma = wcdma != null ? wcdma : new CellSignalStrengthWcdma();
        mTdscdma = tdscdma != null ? tdscdma : new CellSignalStrengthTdscdma();
        mLte = lte != null ? lte : new CellSignalStrengthLte();
        mNr = nr != null ? nr : new CellSignalStrengthNr();
        mTimestampMillis = monotonicMillis();
    }

    private static long monotonicMillis() {
        try {
            return SystemClock.elapsedRealtime();
        } catch (LinkageError e) {
            return System.nanoTime() / 1000000L;
        }
    }

    public SignalStrength(SignalStrength s) {
        mCdma = s.mCdma;
        mGsm = s.mGsm;
        mWcdma = s.mWcdma;
        mTdscdma = s.mTdscdma;
        mLte = s.mLte;
        mNr = s.mNr;
        mTimestampMillis = s.mTimestampMillis;
    }

    private SignalStrength(Parcel in) {
        mCdma = CellSignalStrengthCdma.CREATOR.createFromParcel(in);
        mGsm = CellSignalStrengthGsm.CREATOR.createFromParcel(in);
        mWcdma = CellSignalStrengthWcdma.CREATOR.createFromParcel(in);
        mTdscdma = CellSignalStrengthTdscdma.CREATOR.createFromParcel(in);
        mLte = CellSignalStrengthLte.CREATOR.createFromParcel(in);
        mNr = CellSignalStrengthNr.CREATOR.createFromParcel(in);
        mTimestampMillis = in.readLong();
    }

    public List<CellSignalStrength> getCellSignalStrengths() {
        return getCellSignalStrengths(CellSignalStrength.class);
    }

    public <T extends CellSignalStrength> List<T> getCellSignalStrengths(Class<T> clazz) {
        Objects.requireNonNull(clazz, "clazz must not be null");
        List<T> results = new ArrayList<T>(2);
        addIfValidAndMatches(results, clazz, mLte);
        addIfValidAndMatches(results, clazz, mCdma);
        addIfValidAndMatches(results, clazz, mTdscdma);
        addIfValidAndMatches(results, clazz, mWcdma);
        addIfValidAndMatches(results, clazz, mGsm);
        addIfValidAndMatches(results, clazz, mNr);
        return results;
    }

    private static <T extends CellSignalStrength> void addIfValidAndMatches(
            List<T> out, Class<T> clazz, CellSignalStrength css) {
        if (css != null && css.getDbm() != CellInfo.UNAVAILABLE && clazz.isInstance(css)) {
            out.add(clazz.cast(css));
        }
    }

    public void writeToParcel(Parcel out, int flags) {
        mCdma.writeToParcel(out, flags);
        mGsm.writeToParcel(out, flags);
        mWcdma.writeToParcel(out, flags);
        mTdscdma.writeToParcel(out, flags);
        mLte.writeToParcel(out, flags);
        mNr.writeToParcel(out, flags);
        out.writeLong(mTimestampMillis);
    }

    public long getTimestampMillis() {
        return mTimestampMillis;
    }

    public int describeContents() {
        return 0;
    }

    @Deprecated
    public int getGsmSignalStrength() {
        return mGsm.getAsuLevel();
    }

    @Deprecated
    public int getGsmBitErrorRate() {
        int ber = mGsm.getBitErrorRate();
        return ber == CellInfo.UNAVAILABLE ? -1 : ber;
    }

    @Deprecated
    public int getCdmaDbm() {
        int dbm = mCdma.getCdmaDbm();
        return dbm == CellInfo.UNAVAILABLE ? -120 : dbm;
    }

    @Deprecated
    public int getCdmaEcio() {
        int ecio = mCdma.getCdmaEcio();
        return ecio == CellInfo.UNAVAILABLE ? -160 : ecio;
    }

    @Deprecated
    public int getEvdoDbm() {
        int dbm = mCdma.getEvdoDbm();
        return dbm == CellInfo.UNAVAILABLE ? -120 : dbm;
    }

    @Deprecated
    public int getEvdoEcio() {
        int ecio = mCdma.getEvdoEcio();
        return ecio == CellInfo.UNAVAILABLE ? -160 : ecio;
    }

    @Deprecated
    public int getEvdoSnr() {
        int snr = mCdma.getEvdoSnr();
        return snr == CellInfo.UNAVAILABLE ? -1 : snr;
    }

    public int getLevel() {
        int maxLevel = CellSignalStrength.SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
        CellSignalStrength[] all = new CellSignalStrength[] {
            mLte, mNr, mWcdma, mTdscdma, mGsm, mCdma
        };
        for (int i = 0; i < all.length; i++) {
            int lvl = all[i].getLevel();
            if (lvl > maxLevel) {
                maxLevel = lvl;
            }
        }
        return maxLevel;
    }

    @Deprecated
    public boolean isGsm() {
        return mCdma.getDbm() == CellInfo.UNAVAILABLE;
    }

    public int hashCode() {
        return Objects.hash(mCdma, mGsm, mWcdma, mTdscdma, mLte, mNr);
    }

    public boolean equals(Object o) {
        if (!(o instanceof SignalStrength)) {
            return false;
        }
        SignalStrength s = (SignalStrength) o;
        return Objects.equals(mCdma, s.mCdma)
                && Objects.equals(mGsm, s.mGsm)
                && Objects.equals(mWcdma, s.mWcdma)
                && Objects.equals(mTdscdma, s.mTdscdma)
                && Objects.equals(mLte, s.mLte)
                && Objects.equals(mNr, s.mNr);
    }

    public String toString() {
        return "SignalStrength:{"
                + "mCdma=" + mCdma
                + ",mGsm=" + mGsm
                + ",mWcdma=" + mWcdma
                + ",mTdscdma=" + mTdscdma
                + ",mLte=" + mLte
                + ",mNr=" + mNr + "}";
    }

    public static final Parcelable.Creator<SignalStrength> CREATOR =
            new Parcelable.Creator<SignalStrength>() {
                public SignalStrength createFromParcel(Parcel in) {
                    return new SignalStrength(in);
                }

                public SignalStrength[] newArray(int size) {
                    return new SignalStrength[size];
                }
            };
}
