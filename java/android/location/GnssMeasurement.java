// SPDX-License-Identifier: GPL-3.0-only
package android.location;

import android.os.Parcel;
import android.os.Parcelable;

public final class GnssMeasurement implements Parcelable {
    private static final int HAS_NO_FLAGS = 0;
    private static final int HAS_SNR = 1 << 0;
    private static final int HAS_CARRIER_FREQUENCY = 1 << 1;
    private static final int HAS_CARRIER_CYCLES = 1 << 2;
    private static final int HAS_CARRIER_PHASE = 1 << 3;
    private static final int HAS_CARRIER_PHASE_UNCERTAINTY = 1 << 4;
    private static final int HAS_AUTOMATIC_GAIN_CONTROL = 1 << 5;
    private static final int HAS_CODE_TYPE = 1 << 6;
    private static final int HAS_BASEBAND_CN0 = 1 << 7;
    private static final int HAS_FULL_ISB = 1 << 8;
    private static final int HAS_FULL_ISB_UNCERTAINTY = 1 << 9;
    private static final int HAS_SATELLITE_ISB = 1 << 10;
    private static final int HAS_SATELLITE_ISB_UNCERTAINTY = 1 << 11;

    public static final int MULTIPATH_INDICATOR_UNKNOWN = 0;
    public static final int MULTIPATH_INDICATOR_DETECTED = 1;
    public static final int MULTIPATH_INDICATOR_NOT_DETECTED = 2;

    public static final int STATE_UNKNOWN = 0;
    public static final int STATE_CODE_LOCK = 1;
    public static final int STATE_BIT_SYNC = 2;
    public static final int STATE_SUBFRAME_SYNC = 4;
    public static final int STATE_TOW_DECODED = 8;
    public static final int STATE_MSEC_AMBIGUOUS = 16;
    public static final int STATE_SYMBOL_SYNC = 32;
    public static final int STATE_GLO_STRING_SYNC = 64;
    public static final int STATE_GLO_TOD_DECODED = 128;
    public static final int STATE_BDS_D2_BIT_SYNC = 256;
    public static final int STATE_BDS_D2_SUBFRAME_SYNC = 512;
    public static final int STATE_GAL_E1BC_CODE_LOCK = 1024;
    public static final int STATE_GAL_E1C_2ND_CODE_LOCK = 2048;
    public static final int STATE_GAL_E1B_PAGE_SYNC = 4096;
    public static final int STATE_SBAS_SYNC = 8192;
    public static final int STATE_TOW_KNOWN = 16384;
    public static final int STATE_GLO_TOD_KNOWN = 32768;
    public static final int STATE_2ND_CODE_LOCK = 65536;

    public static final int ADR_STATE_UNKNOWN = 0;
    public static final int ADR_STATE_VALID = 1;
    public static final int ADR_STATE_RESET = 2;
    public static final int ADR_STATE_CYCLE_SLIP = 4;
    public static final int ADR_STATE_HALF_CYCLE_RESOLVED = 8;
    public static final int ADR_STATE_HALF_CYCLE_REPORTED = 16;

    private int mFlags;
    private int mSvid;
    private int mConstellationType;
    private double mTimeOffsetNanos;
    private int mState;
    private long mReceivedSvTimeNanos;
    private long mReceivedSvTimeUncertaintyNanos;
    private double mCn0DbHz;
    private double mBasebandCn0DbHz;
    private double mPseudorangeRateMetersPerSecond;
    private double mPseudorangeRateUncertaintyMetersPerSecond;
    private int mAccumulatedDeltaRangeState;
    private double mAccumulatedDeltaRangeMeters;
    private double mAccumulatedDeltaRangeUncertaintyMeters;
    private float mCarrierFrequencyHz;
    private long mCarrierCycles;
    private double mCarrierPhase;
    private double mCarrierPhaseUncertainty;
    private int mMultipathIndicator;
    private double mSnrInDb;
    private double mAutomaticGainControlLevelInDb;
    private String mCodeType;
    private double mFullInterSignalBiasNanos;
    private double mFullInterSignalBiasUncertaintyNanos;
    private double mSatelliteInterSignalBiasNanos;
    private double mSatelliteInterSignalBiasUncertaintyNanos;

    public GnssMeasurement() {
        mFlags = HAS_NO_FLAGS;
        mConstellationType = GnssStatus.CONSTELLATION_UNKNOWN;
        mState = STATE_UNKNOWN;
        mAccumulatedDeltaRangeState = ADR_STATE_UNKNOWN;
        mMultipathIndicator = MULTIPATH_INDICATOR_UNKNOWN;
        mCarrierFrequencyHz = Float.NaN;
        mCarrierCycles = Long.MIN_VALUE;
        mCarrierPhase = Double.NaN;
        mCarrierPhaseUncertainty = Double.NaN;
        mSnrInDb = Double.NaN;
        mAutomaticGainControlLevelInDb = Double.NaN;
        mCodeType = "UNKNOWN";
        mBasebandCn0DbHz = Double.NaN;
        mFullInterSignalBiasNanos = Double.NaN;
        mFullInterSignalBiasUncertaintyNanos = Double.NaN;
        mSatelliteInterSignalBiasNanos = Double.NaN;
        mSatelliteInterSignalBiasUncertaintyNanos = Double.NaN;
    }

    public int getSvid() {
        return mSvid;
    }

    public void setSvid(int value) {
        mSvid = value;
    }

    public int getConstellationType() {
        return mConstellationType;
    }

    public void setConstellationType(int value) {
        mConstellationType = value;
    }

    public double getTimeOffsetNanos() {
        return mTimeOffsetNanos;
    }

    public void setTimeOffsetNanos(double value) {
        mTimeOffsetNanos = value;
    }

    public int getState() {
        return mState;
    }

    public void setState(int value) {
        mState = value;
    }

    public long getReceivedSvTimeNanos() {
        return mReceivedSvTimeNanos;
    }

    public void setReceivedSvTimeNanos(long value) {
        mReceivedSvTimeNanos = value;
    }

    public long getReceivedSvTimeUncertaintyNanos() {
        return mReceivedSvTimeUncertaintyNanos;
    }

    public void setReceivedSvTimeUncertaintyNanos(long value) {
        mReceivedSvTimeUncertaintyNanos = value;
    }

    public double getCn0DbHz() {
        return mCn0DbHz;
    }

    public void setCn0DbHz(double value) {
        mCn0DbHz = value;
    }

    public boolean hasBasebandCn0DbHz() {
        return (mFlags & HAS_BASEBAND_CN0) != 0;
    }

    public double getBasebandCn0DbHz() {
        return mBasebandCn0DbHz;
    }

    public void setBasebandCn0DbHz(double value) {
        mFlags |= HAS_BASEBAND_CN0;
        mBasebandCn0DbHz = value;
    }

    public void resetBasebandCn0DbHz() {
        mFlags &= ~HAS_BASEBAND_CN0;
        mBasebandCn0DbHz = Double.NaN;
    }

    public double getPseudorangeRateMetersPerSecond() {
        return mPseudorangeRateMetersPerSecond;
    }

    public void setPseudorangeRateMetersPerSecond(double value) {
        mPseudorangeRateMetersPerSecond = value;
    }

    public double getPseudorangeRateUncertaintyMetersPerSecond() {
        return mPseudorangeRateUncertaintyMetersPerSecond;
    }

    public void setPseudorangeRateUncertaintyMetersPerSecond(double value) {
        mPseudorangeRateUncertaintyMetersPerSecond = value;
    }

    public int getAccumulatedDeltaRangeState() {
        return mAccumulatedDeltaRangeState;
    }

    public void setAccumulatedDeltaRangeState(int value) {
        mAccumulatedDeltaRangeState = value;
    }

    public double getAccumulatedDeltaRangeMeters() {
        return mAccumulatedDeltaRangeMeters;
    }

    public void setAccumulatedDeltaRangeMeters(double value) {
        mAccumulatedDeltaRangeMeters = value;
    }

    public double getAccumulatedDeltaRangeUncertaintyMeters() {
        return mAccumulatedDeltaRangeUncertaintyMeters;
    }

    public void setAccumulatedDeltaRangeUncertaintyMeters(double value) {
        mAccumulatedDeltaRangeUncertaintyMeters = value;
    }

    public boolean hasCarrierFrequencyHz() {
        return (mFlags & HAS_CARRIER_FREQUENCY) != 0;
    }

    public float getCarrierFrequencyHz() {
        return mCarrierFrequencyHz;
    }

    public void setCarrierFrequencyHz(float carrierFrequencyHz) {
        mFlags |= HAS_CARRIER_FREQUENCY;
        mCarrierFrequencyHz = carrierFrequencyHz;
    }

    public void resetCarrierFrequencyHz() {
        mFlags &= ~HAS_CARRIER_FREQUENCY;
        mCarrierFrequencyHz = Float.NaN;
    }

    public boolean hasCarrierCycles() {
        return (mFlags & HAS_CARRIER_CYCLES) != 0;
    }

    public long getCarrierCycles() {
        return mCarrierCycles;
    }

    public void setCarrierCycles(long value) {
        mFlags |= HAS_CARRIER_CYCLES;
        mCarrierCycles = value;
    }

    public void resetCarrierCycles() {
        mFlags &= ~HAS_CARRIER_CYCLES;
        mCarrierCycles = Long.MIN_VALUE;
    }

    public boolean hasCarrierPhase() {
        return (mFlags & HAS_CARRIER_PHASE) != 0;
    }

    public double getCarrierPhase() {
        return mCarrierPhase;
    }

    public void setCarrierPhase(double value) {
        mFlags |= HAS_CARRIER_PHASE;
        mCarrierPhase = value;
    }

    public void resetCarrierPhase() {
        mFlags &= ~HAS_CARRIER_PHASE;
        mCarrierPhase = Double.NaN;
    }

    public boolean hasCarrierPhaseUncertainty() {
        return (mFlags & HAS_CARRIER_PHASE_UNCERTAINTY) != 0;
    }

    public double getCarrierPhaseUncertainty() {
        return mCarrierPhaseUncertainty;
    }

    public void setCarrierPhaseUncertainty(double value) {
        mFlags |= HAS_CARRIER_PHASE_UNCERTAINTY;
        mCarrierPhaseUncertainty = value;
    }

    public void resetCarrierPhaseUncertainty() {
        mFlags &= ~HAS_CARRIER_PHASE_UNCERTAINTY;
        mCarrierPhaseUncertainty = Double.NaN;
    }

    public int getMultipathIndicator() {
        return mMultipathIndicator;
    }

    public void setMultipathIndicator(int value) {
        mMultipathIndicator = value;
    }

    public boolean hasSnrInDb() {
        return (mFlags & HAS_SNR) != 0;
    }

    public double getSnrInDb() {
        return mSnrInDb;
    }

    public void setSnrInDb(double snrInDb) {
        mFlags |= HAS_SNR;
        mSnrInDb = snrInDb;
    }

    public void resetSnrInDb() {
        mFlags &= ~HAS_SNR;
        mSnrInDb = Double.NaN;
    }

    public boolean hasAutomaticGainControlLevelDb() {
        return (mFlags & HAS_AUTOMATIC_GAIN_CONTROL) != 0;
    }

    public double getAutomaticGainControlLevelDb() {
        return mAutomaticGainControlLevelInDb;
    }

    public void setAutomaticGainControlLevelInDb(double agcLevelDb) {
        mFlags |= HAS_AUTOMATIC_GAIN_CONTROL;
        mAutomaticGainControlLevelInDb = agcLevelDb;
    }

    public void resetAutomaticGainControlLevel() {
        mFlags &= ~HAS_AUTOMATIC_GAIN_CONTROL;
        mAutomaticGainControlLevelInDb = Double.NaN;
    }

    public boolean hasCodeType() {
        return (mFlags & HAS_CODE_TYPE) != 0;
    }

    public String getCodeType() {
        return mCodeType;
    }

    public void setCodeType(String codeType) {
        mFlags |= HAS_CODE_TYPE;
        mCodeType = codeType;
    }

    public void resetCodeType() {
        mFlags &= ~HAS_CODE_TYPE;
        mCodeType = "UNKNOWN";
    }

    public boolean hasFullInterSignalBiasNanos() {
        return (mFlags & HAS_FULL_ISB) != 0;
    }

    public double getFullInterSignalBiasNanos() {
        return mFullInterSignalBiasNanos;
    }

    public void setFullInterSignalBiasNanos(double fullInterSignalBiasNanos) {
        mFlags |= HAS_FULL_ISB;
        mFullInterSignalBiasNanos = fullInterSignalBiasNanos;
    }

    public void resetFullInterSignalBiasNanos() {
        mFlags &= ~HAS_FULL_ISB;
        mFullInterSignalBiasNanos = Double.NaN;
    }

    public boolean hasFullInterSignalBiasUncertaintyNanos() {
        return (mFlags & HAS_FULL_ISB_UNCERTAINTY) != 0;
    }

    public double getFullInterSignalBiasUncertaintyNanos() {
        return mFullInterSignalBiasUncertaintyNanos;
    }

    public void setFullInterSignalBiasUncertaintyNanos(double fullInterSignalBiasUncertaintyNanos) {
        mFlags |= HAS_FULL_ISB_UNCERTAINTY;
        mFullInterSignalBiasUncertaintyNanos = fullInterSignalBiasUncertaintyNanos;
    }

    public void resetFullInterSignalBiasUncertaintyNanos() {
        mFlags &= ~HAS_FULL_ISB_UNCERTAINTY;
        mFullInterSignalBiasUncertaintyNanos = Double.NaN;
    }

    public boolean hasSatelliteInterSignalBiasNanos() {
        return (mFlags & HAS_SATELLITE_ISB) != 0;
    }

    public double getSatelliteInterSignalBiasNanos() {
        return mSatelliteInterSignalBiasNanos;
    }

    public void setSatelliteInterSignalBiasNanos(double satelliteInterSignalBiasNanos) {
        mFlags |= HAS_SATELLITE_ISB;
        mSatelliteInterSignalBiasNanos = satelliteInterSignalBiasNanos;
    }

    public void resetSatelliteInterSignalBiasNanos() {
        mFlags &= ~HAS_SATELLITE_ISB;
        mSatelliteInterSignalBiasNanos = Double.NaN;
    }

    public boolean hasSatelliteInterSignalBiasUncertaintyNanos() {
        return (mFlags & HAS_SATELLITE_ISB_UNCERTAINTY) != 0;
    }

    public double getSatelliteInterSignalBiasUncertaintyNanos() {
        return mSatelliteInterSignalBiasUncertaintyNanos;
    }

    public void setSatelliteInterSignalBiasUncertaintyNanos(double satelliteInterSignalBiasUncertaintyNanos) {
        mFlags |= HAS_SATELLITE_ISB_UNCERTAINTY;
        mSatelliteInterSignalBiasUncertaintyNanos = satelliteInterSignalBiasUncertaintyNanos;
    }

    public void resetSatelliteInterSignalBiasUncertaintyNanos() {
        mFlags &= ~HAS_SATELLITE_ISB_UNCERTAINTY;
        mSatelliteInterSignalBiasUncertaintyNanos = Double.NaN;
    }

    public static final Parcelable.Creator<GnssMeasurement> CREATOR =
            new Parcelable.Creator<GnssMeasurement>() {
                @Override
                public GnssMeasurement createFromParcel(Parcel parcel) {
                    GnssMeasurement m = new GnssMeasurement();
                    m.mFlags = parcel.readInt();
                    m.mSvid = parcel.readInt();
                    m.mConstellationType = parcel.readInt();
                    m.mTimeOffsetNanos = parcel.readDouble();
                    m.mState = parcel.readInt();
                    m.mReceivedSvTimeNanos = parcel.readLong();
                    m.mReceivedSvTimeUncertaintyNanos = parcel.readLong();
                    m.mCn0DbHz = parcel.readDouble();
                    m.mPseudorangeRateMetersPerSecond = parcel.readDouble();
                    m.mPseudorangeRateUncertaintyMetersPerSecond = parcel.readDouble();
                    m.mAccumulatedDeltaRangeState = parcel.readInt();
                    m.mAccumulatedDeltaRangeMeters = parcel.readDouble();
                    m.mAccumulatedDeltaRangeUncertaintyMeters = parcel.readDouble();
                    m.mCarrierFrequencyHz = parcel.readFloat();
                    m.mCarrierCycles = parcel.readLong();
                    m.mCarrierPhase = parcel.readDouble();
                    m.mCarrierPhaseUncertainty = parcel.readDouble();
                    m.mMultipathIndicator = parcel.readInt();
                    m.mSnrInDb = parcel.readDouble();
                    m.mAutomaticGainControlLevelInDb = parcel.readDouble();
                    m.mCodeType = parcel.readString();
                    m.mBasebandCn0DbHz = parcel.readDouble();
                    m.mFullInterSignalBiasNanos = parcel.readDouble();
                    m.mFullInterSignalBiasUncertaintyNanos = parcel.readDouble();
                    m.mSatelliteInterSignalBiasNanos = parcel.readDouble();
                    m.mSatelliteInterSignalBiasUncertaintyNanos = parcel.readDouble();
                    return m;
                }

                @Override
                public GnssMeasurement[] newArray(int size) {
                    return new GnssMeasurement[size];
                }
            };

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(mFlags);
        parcel.writeInt(mSvid);
        parcel.writeInt(mConstellationType);
        parcel.writeDouble(mTimeOffsetNanos);
        parcel.writeInt(mState);
        parcel.writeLong(mReceivedSvTimeNanos);
        parcel.writeLong(mReceivedSvTimeUncertaintyNanos);
        parcel.writeDouble(mCn0DbHz);
        parcel.writeDouble(mPseudorangeRateMetersPerSecond);
        parcel.writeDouble(mPseudorangeRateUncertaintyMetersPerSecond);
        parcel.writeInt(mAccumulatedDeltaRangeState);
        parcel.writeDouble(mAccumulatedDeltaRangeMeters);
        parcel.writeDouble(mAccumulatedDeltaRangeUncertaintyMeters);
        parcel.writeFloat(mCarrierFrequencyHz);
        parcel.writeLong(mCarrierCycles);
        parcel.writeDouble(mCarrierPhase);
        parcel.writeDouble(mCarrierPhaseUncertainty);
        parcel.writeInt(mMultipathIndicator);
        parcel.writeDouble(mSnrInDb);
        parcel.writeDouble(mAutomaticGainControlLevelInDb);
        parcel.writeString(mCodeType);
        parcel.writeDouble(mBasebandCn0DbHz);
        parcel.writeDouble(mFullInterSignalBiasNanos);
        parcel.writeDouble(mFullInterSignalBiasUncertaintyNanos);
        parcel.writeDouble(mSatelliteInterSignalBiasNanos);
        parcel.writeDouble(mSatelliteInterSignalBiasUncertaintyNanos);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String toString() {
        return "GnssMeasurement[Svid=" + mSvid
                + ", Constellation=" + mConstellationType
                + ", Cn0DbHz=" + mCn0DbHz
                + ", State=0x" + Integer.toHexString(mState) + "]";
    }
}
