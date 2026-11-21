// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Arrays;
import java.util.Objects;

public final class SignalThresholdInfo implements Parcelable {
    public static final int SIGNAL_MEASUREMENT_TYPE_UNKNOWN = 0;
    public static final int SIGNAL_MEASUREMENT_TYPE_RSSI = 1;
    public static final int SIGNAL_MEASUREMENT_TYPE_RSCP = 2;
    public static final int SIGNAL_MEASUREMENT_TYPE_RSRP = 3;
    public static final int SIGNAL_MEASUREMENT_TYPE_RSRQ = 4;
    public static final int SIGNAL_MEASUREMENT_TYPE_RSSNR = 5;
    public static final int SIGNAL_MEASUREMENT_TYPE_SSRSRP = 6;
    public static final int SIGNAL_MEASUREMENT_TYPE_SSRSRQ = 7;
    public static final int SIGNAL_MEASUREMENT_TYPE_SSSINR = 8;
    public static final int SIGNAL_MEASUREMENT_TYPE_ECNO = 9;

    private static final int MINIMUM_NUMBER_OF_THRESHOLDS_ALLOWED = 1;
    private static final int MAXIMUM_NUMBER_OF_THRESHOLDS_ALLOWED = 4;

    private final int mRan;
    private final int mSignalMeasurementType;
    private final int mHysteresisMs;
    private final int mHysteresisDb;
    private final int[] mThresholds;
    private final boolean mIsEnabled;

    private SignalThresholdInfo(int ran, int signalMeasurementType, int hysteresisMs,
            int hysteresisDb, int[] thresholds, boolean isEnabled) {
        mRan = ran;
        mSignalMeasurementType = signalMeasurementType;
        mHysteresisMs = hysteresisMs;
        mHysteresisDb = hysteresisDb;
        mThresholds = thresholds.clone();
        Arrays.sort(mThresholds);
        mIsEnabled = isEnabled;
    }

    private SignalThresholdInfo(Parcel in) {
        mRan = in.readInt();
        mSignalMeasurementType = in.readInt();
        mHysteresisMs = in.readInt();
        mHysteresisDb = in.readInt();
        int[] t = in.createIntArray();
        mThresholds = t != null ? t : new int[0];
        mIsEnabled = in.readInt() != 0;
    }

    public int getRadioAccessNetworkType() {
        return mRan;
    }

    public int getSignalMeasurementType() {
        return mSignalMeasurementType;
    }

    public int getHysteresisDb() {
        return mHysteresisDb;
    }

    public int[] getThresholds() {
        return mThresholds.clone();
    }

    public static int getMinimumNumberOfThresholdsAllowed() {
        return MINIMUM_NUMBER_OF_THRESHOLDS_ALLOWED;
    }

    public static int getMaximumNumberOfThresholdsAllowed() {
        return MAXIMUM_NUMBER_OF_THRESHOLDS_ALLOWED;
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mRan);
        out.writeInt(mSignalMeasurementType);
        out.writeInt(mHysteresisMs);
        out.writeInt(mHysteresisDb);
        out.writeIntArray(mThresholds);
        out.writeInt(mIsEnabled ? 1 : 0);
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SignalThresholdInfo)) {
            return false;
        }
        SignalThresholdInfo other = (SignalThresholdInfo) o;
        return mRan == other.mRan
                && mSignalMeasurementType == other.mSignalMeasurementType
                && mHysteresisMs == other.mHysteresisMs
                && mHysteresisDb == other.mHysteresisDb
                && Arrays.equals(mThresholds, other.mThresholds)
                && mIsEnabled == other.mIsEnabled;
    }

    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(mRan),
                Integer.valueOf(mSignalMeasurementType),
                Integer.valueOf(mHysteresisMs),
                Integer.valueOf(mHysteresisDb),
                Integer.valueOf(Arrays.hashCode(mThresholds)),
                Boolean.valueOf(mIsEnabled));
    }

    public String toString() {
        return "SignalThresholdInfo{mRan=" + mRan
                + " mSignalMeasurementType=" + mSignalMeasurementType
                + " mHysteresisMs=" + mHysteresisMs
                + " mHysteresisDb=" + mHysteresisDb
                + " mThresholds=" + Arrays.toString(mThresholds)
                + " mIsEnabled=" + mIsEnabled + "}";
    }

    public static final Parcelable.Creator<SignalThresholdInfo> CREATOR =
            new Parcelable.Creator<SignalThresholdInfo>() {
                public SignalThresholdInfo createFromParcel(Parcel in) {
                    return new SignalThresholdInfo(in);
                }

                public SignalThresholdInfo[] newArray(int size) {
                    return new SignalThresholdInfo[size];
                }
            };

    public static final class Builder {
        private int mRan = AccessNetworkConstants.AccessNetworkType.UNKNOWN;
        private int mSignalMeasurementType = SIGNAL_MEASUREMENT_TYPE_UNKNOWN;
        private int mHysteresisDb = 2;
        private int[] mThresholds = null;

        public Builder() {
        }

        public Builder setRadioAccessNetworkType(int ran) {
            mRan = ran;
            return this;
        }

        public Builder setSignalMeasurementType(int signalMeasurementType) {
            mSignalMeasurementType = signalMeasurementType;
            return this;
        }

        public Builder setHysteresisDb(int hysteresisDb) {
            if (hysteresisDb < 0) {
                throw new IllegalArgumentException("hysteresisDb value should not be less than 0");
            }
            mHysteresisDb = hysteresisDb;
            return this;
        }

        public Builder setThresholds(int[] thresholds) {
            Objects.requireNonNull(thresholds, "thresholds must not be null");
            if (thresholds.length < MINIMUM_NUMBER_OF_THRESHOLDS_ALLOWED
                    || thresholds.length > MAXIMUM_NUMBER_OF_THRESHOLDS_ALLOWED) {
                throw new IllegalArgumentException(
                        "thresholds length must between " + MINIMUM_NUMBER_OF_THRESHOLDS_ALLOWED
                        + " and " + MAXIMUM_NUMBER_OF_THRESHOLDS_ALLOWED);
            }
            mThresholds = thresholds.clone();
            return this;
        }

        public SignalThresholdInfo build() {
            if (mSignalMeasurementType < SIGNAL_MEASUREMENT_TYPE_RSSI
                    || mSignalMeasurementType > SIGNAL_MEASUREMENT_TYPE_ECNO) {
                throw new IllegalArgumentException(
                        "Invalid signal measurement type: " + mSignalMeasurementType);
            }
            if (mThresholds == null) {
                throw new IllegalArgumentException("thresholds must be set");
            }
            return new SignalThresholdInfo(
                    mRan, mSignalMeasurementType, 0, mHysteresisDb, mThresholds, false);
        }
    }
}
