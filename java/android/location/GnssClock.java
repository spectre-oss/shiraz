// SPDX-License-Identifier: GPL-3.0-only
package android.location;

import android.os.Parcel;
import android.os.Parcelable;

public final class GnssClock implements Parcelable {
    private static final int HAS_NO_FLAGS = 0;
    private static final int HAS_LEAP_SECOND = 1 << 0;
    private static final int HAS_TIME_UNCERTAINTY = 1 << 1;
    private static final int HAS_FULL_BIAS = 1 << 2;
    private static final int HAS_BIAS = 1 << 3;
    private static final int HAS_BIAS_UNCERTAINTY = 1 << 4;
    private static final int HAS_DRIFT = 1 << 5;
    private static final int HAS_DRIFT_UNCERTAINTY = 1 << 6;
    private static final int HAS_ELAPSED_REALTIME_NANOS = 1 << 7;
    private static final int HAS_ELAPSED_REALTIME_UNCERTAINTY_NANOS = 1 << 8;
    private static final int HAS_REFERENCE_CONSTELLATION_TYPE_FOR_ISB = 1 << 9;
    private static final int HAS_REFERENCE_CARRIER_FREQUENCY_FOR_ISB = 1 << 10;
    private static final int HAS_REFERENCE_CODE_TYPE_FOR_ISB = 1 << 11;

    private int mFlags;
    private int mLeapSecond;
    private long mTimeNanos;
    private double mTimeUncertaintyNanos;
    private long mFullBiasNanos;
    private double mBiasNanos;
    private double mBiasUncertaintyNanos;
    private double mDriftNanosPerSecond;
    private double mDriftUncertaintyNanosPerSecond;
    private int mHardwareClockDiscontinuityCount;
    private long mElapsedRealtimeNanos;
    private double mElapsedRealtimeUncertaintyNanos;
    private int mReferenceConstellationTypeForIsb;
    private double mReferenceCarrierFrequencyHzForIsb;
    private String mReferenceCodeTypeForIsb;

    public GnssClock() {
        mFlags = HAS_NO_FLAGS;
        mReferenceConstellationTypeForIsb = GnssStatus.CONSTELLATION_UNKNOWN;
        mReferenceCodeTypeForIsb = "UNKNOWN";
    }

    public boolean hasLeapSecond() {
        return (mFlags & HAS_LEAP_SECOND) != 0;
    }

    public int getLeapSecond() {
        return mLeapSecond;
    }

    public void setLeapSecond(int leapSecond) {
        mFlags |= HAS_LEAP_SECOND;
        mLeapSecond = leapSecond;
    }

    public void resetLeapSecond() {
        mFlags &= ~HAS_LEAP_SECOND;
        mLeapSecond = Integer.MIN_VALUE;
    }

    public long getTimeNanos() {
        return mTimeNanos;
    }

    public void setTimeNanos(long timeNanos) {
        mTimeNanos = timeNanos;
    }

    public boolean hasTimeUncertaintyNanos() {
        return (mFlags & HAS_TIME_UNCERTAINTY) != 0;
    }

    public double getTimeUncertaintyNanos() {
        return mTimeUncertaintyNanos;
    }

    public void setTimeUncertaintyNanos(double timeUncertaintyNanos) {
        mFlags |= HAS_TIME_UNCERTAINTY;
        mTimeUncertaintyNanos = timeUncertaintyNanos;
    }

    public void resetTimeUncertaintyNanos() {
        mFlags &= ~HAS_TIME_UNCERTAINTY;
        mTimeUncertaintyNanos = Double.NaN;
    }

    public boolean hasFullBiasNanos() {
        return (mFlags & HAS_FULL_BIAS) != 0;
    }

    public long getFullBiasNanos() {
        return mFullBiasNanos;
    }

    public void setFullBiasNanos(long value) {
        mFlags |= HAS_FULL_BIAS;
        mFullBiasNanos = value;
    }

    public void resetFullBiasNanos() {
        mFlags &= ~HAS_FULL_BIAS;
        mFullBiasNanos = Long.MIN_VALUE;
    }

    public boolean hasBiasNanos() {
        return (mFlags & HAS_BIAS) != 0;
    }

    public double getBiasNanos() {
        return mBiasNanos;
    }

    public void setBiasNanos(double biasNanos) {
        mFlags |= HAS_BIAS;
        mBiasNanos = biasNanos;
    }

    public void resetBiasNanos() {
        mFlags &= ~HAS_BIAS;
        mBiasNanos = Double.NaN;
    }

    public boolean hasBiasUncertaintyNanos() {
        return (mFlags & HAS_BIAS_UNCERTAINTY) != 0;
    }

    public double getBiasUncertaintyNanos() {
        return mBiasUncertaintyNanos;
    }

    public void setBiasUncertaintyNanos(double biasUncertaintyNanos) {
        mFlags |= HAS_BIAS_UNCERTAINTY;
        mBiasUncertaintyNanos = biasUncertaintyNanos;
    }

    public void resetBiasUncertaintyNanos() {
        mFlags &= ~HAS_BIAS_UNCERTAINTY;
        mBiasUncertaintyNanos = Double.NaN;
    }

    public boolean hasDriftNanosPerSecond() {
        return (mFlags & HAS_DRIFT) != 0;
    }

    public double getDriftNanosPerSecond() {
        return mDriftNanosPerSecond;
    }

    public void setDriftNanosPerSecond(double driftNanosPerSecond) {
        mFlags |= HAS_DRIFT;
        mDriftNanosPerSecond = driftNanosPerSecond;
    }

    public void resetDriftNanosPerSecond() {
        mFlags &= ~HAS_DRIFT;
        mDriftNanosPerSecond = Double.NaN;
    }

    public boolean hasDriftUncertaintyNanosPerSecond() {
        return (mFlags & HAS_DRIFT_UNCERTAINTY) != 0;
    }

    public double getDriftUncertaintyNanosPerSecond() {
        return mDriftUncertaintyNanosPerSecond;
    }

    public void setDriftUncertaintyNanosPerSecond(double driftUncertaintyNanosPerSecond) {
        mFlags |= HAS_DRIFT_UNCERTAINTY;
        mDriftUncertaintyNanosPerSecond = driftUncertaintyNanosPerSecond;
    }

    public void resetDriftUncertaintyNanosPerSecond() {
        mFlags &= ~HAS_DRIFT_UNCERTAINTY;
        mDriftUncertaintyNanosPerSecond = Double.NaN;
    }

    public boolean hasElapsedRealtimeNanos() {
        return (mFlags & HAS_ELAPSED_REALTIME_NANOS) != 0;
    }

    public long getElapsedRealtimeNanos() {
        return mElapsedRealtimeNanos;
    }

    public void setElapsedRealtimeNanos(long elapsedRealtimeNanos) {
        mFlags |= HAS_ELAPSED_REALTIME_NANOS;
        mElapsedRealtimeNanos = elapsedRealtimeNanos;
    }

    public void resetElapsedRealtimeNanos() {
        mFlags &= ~HAS_ELAPSED_REALTIME_NANOS;
        mElapsedRealtimeNanos = 0L;
    }

    public boolean hasElapsedRealtimeUncertaintyNanos() {
        return (mFlags & HAS_ELAPSED_REALTIME_UNCERTAINTY_NANOS) != 0;
    }

    public double getElapsedRealtimeUncertaintyNanos() {
        return mElapsedRealtimeUncertaintyNanos;
    }

    public void setElapsedRealtimeUncertaintyNanos(double elapsedRealtimeUncertaintyNanos) {
        mFlags |= HAS_ELAPSED_REALTIME_UNCERTAINTY_NANOS;
        mElapsedRealtimeUncertaintyNanos = elapsedRealtimeUncertaintyNanos;
    }

    public void resetElapsedRealtimeUncertaintyNanos() {
        mFlags &= ~HAS_ELAPSED_REALTIME_UNCERTAINTY_NANOS;
        mElapsedRealtimeUncertaintyNanos = Double.NaN;
    }

    public boolean hasReferenceConstellationTypeForIsb() {
        return (mFlags & HAS_REFERENCE_CONSTELLATION_TYPE_FOR_ISB) != 0;
    }

    public int getReferenceConstellationTypeForIsb() {
        return mReferenceConstellationTypeForIsb;
    }

    public void setReferenceConstellationTypeForIsb(int value) {
        mFlags |= HAS_REFERENCE_CONSTELLATION_TYPE_FOR_ISB;
        mReferenceConstellationTypeForIsb = value;
    }

    public void resetReferenceConstellationTypeForIsb() {
        mFlags &= ~HAS_REFERENCE_CONSTELLATION_TYPE_FOR_ISB;
        mReferenceConstellationTypeForIsb = GnssStatus.CONSTELLATION_UNKNOWN;
    }

    public boolean hasReferenceCarrierFrequencyHzForIsb() {
        return (mFlags & HAS_REFERENCE_CARRIER_FREQUENCY_FOR_ISB) != 0;
    }

    public double getReferenceCarrierFrequencyHzForIsb() {
        return mReferenceCarrierFrequencyHzForIsb;
    }

    public void setReferenceCarrierFrequencyHzForIsb(double value) {
        mFlags |= HAS_REFERENCE_CARRIER_FREQUENCY_FOR_ISB;
        mReferenceCarrierFrequencyHzForIsb = value;
    }

    public void resetReferenceCarrierFrequencyHzForIsb() {
        mFlags &= ~HAS_REFERENCE_CARRIER_FREQUENCY_FOR_ISB;
        mReferenceCarrierFrequencyHzForIsb = Double.NaN;
    }

    public boolean hasReferenceCodeTypeForIsb() {
        return (mFlags & HAS_REFERENCE_CODE_TYPE_FOR_ISB) != 0;
    }

    public String getReferenceCodeTypeForIsb() {
        return mReferenceCodeTypeForIsb;
    }

    public void setReferenceCodeTypeForIsb(String codeType) {
        mFlags |= HAS_REFERENCE_CODE_TYPE_FOR_ISB;
        mReferenceCodeTypeForIsb = codeType;
    }

    public void resetReferenceCodeTypeForIsb() {
        mFlags &= ~HAS_REFERENCE_CODE_TYPE_FOR_ISB;
        mReferenceCodeTypeForIsb = "UNKNOWN";
    }

    public int getHardwareClockDiscontinuityCount() {
        return mHardwareClockDiscontinuityCount;
    }

    public void setHardwareClockDiscontinuityCount(int value) {
        mHardwareClockDiscontinuityCount = value;
    }

    public static final Parcelable.Creator<GnssClock> CREATOR =
            new Parcelable.Creator<GnssClock>() {
                @Override
                public GnssClock createFromParcel(Parcel parcel) {
                    GnssClock gpsClock = new GnssClock();
                    gpsClock.mFlags = parcel.readInt();
                    gpsClock.mLeapSecond = parcel.readInt();
                    gpsClock.mTimeNanos = parcel.readLong();
                    gpsClock.mTimeUncertaintyNanos = parcel.readDouble();
                    gpsClock.mFullBiasNanos = parcel.readLong();
                    gpsClock.mBiasNanos = parcel.readDouble();
                    gpsClock.mBiasUncertaintyNanos = parcel.readDouble();
                    gpsClock.mDriftNanosPerSecond = parcel.readDouble();
                    gpsClock.mDriftUncertaintyNanosPerSecond = parcel.readDouble();
                    gpsClock.mHardwareClockDiscontinuityCount = parcel.readInt();
                    gpsClock.mElapsedRealtimeNanos = parcel.readLong();
                    gpsClock.mElapsedRealtimeUncertaintyNanos = parcel.readDouble();
                    gpsClock.mReferenceConstellationTypeForIsb = parcel.readInt();
                    gpsClock.mReferenceCarrierFrequencyHzForIsb = parcel.readDouble();
                    gpsClock.mReferenceCodeTypeForIsb = parcel.readString();
                    return gpsClock;
                }

                @Override
                public GnssClock[] newArray(int size) {
                    return new GnssClock[size];
                }
            };

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(mFlags);
        parcel.writeInt(mLeapSecond);
        parcel.writeLong(mTimeNanos);
        parcel.writeDouble(mTimeUncertaintyNanos);
        parcel.writeLong(mFullBiasNanos);
        parcel.writeDouble(mBiasNanos);
        parcel.writeDouble(mBiasUncertaintyNanos);
        parcel.writeDouble(mDriftNanosPerSecond);
        parcel.writeDouble(mDriftUncertaintyNanosPerSecond);
        parcel.writeInt(mHardwareClockDiscontinuityCount);
        parcel.writeLong(mElapsedRealtimeNanos);
        parcel.writeDouble(mElapsedRealtimeUncertaintyNanos);
        parcel.writeInt(mReferenceConstellationTypeForIsb);
        parcel.writeDouble(mReferenceCarrierFrequencyHzForIsb);
        parcel.writeString(mReferenceCodeTypeForIsb);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String toString() {
        return "GnssClock[TimeNanos=" + mTimeNanos
                + ", Flags=0x" + Integer.toHexString(mFlags)
                + ", DiscontinuityCount=" + mHardwareClockDiscontinuityCount + "]";
    }
}
