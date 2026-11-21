// SPDX-License-Identifier: GPL-3.0-only
package android.location;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class GnssCapabilities implements Parcelable {
    public static final int CAPABILITY_UNKNOWN = 0;
    public static final int CAPABILITY_SUPPORTED = 1;
    public static final int CAPABILITY_UNSUPPORTED = 2;

    private static final int TOP_HAL_CAPABILITY_SCHEDULING = 1 << 0;
    private static final int TOP_HAL_CAPABILITY_MSB = 1 << 1;
    private static final int TOP_HAL_CAPABILITY_MSA = 1 << 2;
    private static final int TOP_HAL_CAPABILITY_SINGLE_SHOT = 1 << 3;
    private static final int TOP_HAL_CAPABILITY_ON_DEMAND_TIME = 1 << 4;
    private static final int TOP_HAL_CAPABILITY_GEOFENCING = 1 << 5;
    private static final int TOP_HAL_CAPABILITY_MEASUREMENTS = 1 << 6;
    private static final int TOP_HAL_CAPABILITY_NAV_MESSAGES = 1 << 7;
    private static final int TOP_HAL_CAPABILITY_LOW_POWER_MODE = 1 << 8;
    private static final int TOP_HAL_CAPABILITY_SATELLITE_BLOCKLIST = 1 << 9;
    private static final int TOP_HAL_CAPABILITY_MEASUREMENT_CORRECTIONS = 1 << 10;
    private static final int TOP_HAL_CAPABILITY_ANTENNA_INFO = 1 << 11;
    private static final int TOP_HAL_CAPABILITY_CORRELATION_VECTOR = 1 << 12;
    private static final int TOP_HAL_CAPABILITY_SATELLITE_PVT = 1 << 13;
    private static final int TOP_HAL_CAPABILITY_MEASUREMENT_CORRECTIONS_FOR_DRIVING = 1 << 14;

    private static final int SUB_HAL_MEASUREMENT_CORRECTIONS_CAPABILITY_LOS_SATS = 1 << 0;
    private static final int SUB_HAL_MEASUREMENT_CORRECTIONS_CAPABILITY_EXCESS_PATH_LENGTH = 1 << 1;
    private static final int SUB_HAL_MEASUREMENT_CORRECTIONS_CAPABILITY_REFLECTING_PLANE = 1 << 2;

    private static final int SUB_HAL_POWER_CAPABILITY_TOTAL = 1 << 0;
    private static final int SUB_HAL_POWER_CAPABILITY_SINGLEBAND_TRACKING = 1 << 1;
    private static final int SUB_HAL_POWER_CAPABILITY_MULTIBAND_TRACKING = 1 << 2;
    private static final int SUB_HAL_POWER_CAPABILITY_SINGLEBAND_ACQUISITION = 1 << 3;
    private static final int SUB_HAL_POWER_CAPABILITY_MULTIBAND_ACQUISITION = 1 << 4;
    private static final int SUB_HAL_POWER_CAPABILITY_OTHER_MODES = 1 << 5;

    private final int mTopFlags;
    private final int mAdrCapability;
    private final int mMeasurementCorrectionsFlags;
    private final int mPowerFlags;
    private final List<GnssSignalType> mGnssSignalTypes;

    private GnssCapabilities(
            int topFlags,
            int adrCapability,
            int measurementCorrectionsFlags,
            int powerFlags,
            List<GnssSignalType> gnssSignalTypes) {
        mTopFlags = topFlags;
        mAdrCapability = adrCapability;
        mMeasurementCorrectionsFlags = measurementCorrectionsFlags;
        mPowerFlags = powerFlags;
        mGnssSignalTypes = gnssSignalTypes == null || gnssSignalTypes.isEmpty()
                ? Collections.<GnssSignalType>emptyList()
                : Collections.unmodifiableList(new ArrayList<GnssSignalType>(gnssSignalTypes));
    }

    public boolean hasScheduling() {
        return (mTopFlags & TOP_HAL_CAPABILITY_SCHEDULING) != 0;
    }

    public boolean hasMsb() {
        return (mTopFlags & TOP_HAL_CAPABILITY_MSB) != 0;
    }

    public boolean hasMsa() {
        return (mTopFlags & TOP_HAL_CAPABILITY_MSA) != 0;
    }

    public boolean hasSingleShotFix() {
        return (mTopFlags & TOP_HAL_CAPABILITY_SINGLE_SHOT) != 0;
    }

    public boolean hasOnDemandTime() {
        return (mTopFlags & TOP_HAL_CAPABILITY_ON_DEMAND_TIME) != 0;
    }

    public boolean hasGeofencing() {
        return (mTopFlags & TOP_HAL_CAPABILITY_GEOFENCING) != 0;
    }

    public boolean hasMeasurements() {
        return (mTopFlags & TOP_HAL_CAPABILITY_MEASUREMENTS) != 0;
    }

    @Deprecated
    public boolean hasNavigationMessages() {
        return (mTopFlags & TOP_HAL_CAPABILITY_NAV_MESSAGES) != 0;
    }

    @Deprecated
    public boolean hasLowPowerMode() {
        return (mTopFlags & TOP_HAL_CAPABILITY_LOW_POWER_MODE) != 0;
    }

    @Deprecated
    public boolean hasSatelliteBlocklist() {
        return (mTopFlags & TOP_HAL_CAPABILITY_SATELLITE_BLOCKLIST) != 0;
    }

    public boolean hasSatellitePvt() {
        return (mTopFlags & TOP_HAL_CAPABILITY_SATELLITE_PVT) != 0;
    }

    public boolean hasMeasurementCorrections() {
        return (mTopFlags & TOP_HAL_CAPABILITY_MEASUREMENT_CORRECTIONS) != 0;
    }

    @Deprecated
    public boolean hasGnssAntennaInfo() {
        return hasAntennaInfo();
    }

    public boolean hasAntennaInfo() {
        return (mTopFlags & TOP_HAL_CAPABILITY_ANTENNA_INFO) != 0;
    }

    public boolean hasMeasurementCorrelationVectors() {
        return (mTopFlags & TOP_HAL_CAPABILITY_CORRELATION_VECTOR) != 0;
    }

    public boolean hasMeasurementCorrectionsForDriving() {
        return (mTopFlags & TOP_HAL_CAPABILITY_MEASUREMENT_CORRECTIONS_FOR_DRIVING) != 0;
    }

    public int hasAccumulatedDeltaRange() {
        return mAdrCapability;
    }

    public boolean hasMeasurementCorrectionsLosSats() {
        return (mMeasurementCorrectionsFlags
                & SUB_HAL_MEASUREMENT_CORRECTIONS_CAPABILITY_LOS_SATS) != 0;
    }

    public boolean hasMeasurementCorrectionsExcessPathLength() {
        return (mMeasurementCorrectionsFlags
                & SUB_HAL_MEASUREMENT_CORRECTIONS_CAPABILITY_EXCESS_PATH_LENGTH) != 0;
    }

    public boolean hasMeasurementCorrectionsReflectingPlane() {
        return (mMeasurementCorrectionsFlags
                & SUB_HAL_MEASUREMENT_CORRECTIONS_CAPABILITY_REFLECTING_PLANE) != 0;
    }

    public boolean hasPowerTotal() {
        return (mPowerFlags & SUB_HAL_POWER_CAPABILITY_TOTAL) != 0;
    }

    public boolean hasPowerSinglebandTracking() {
        return (mPowerFlags & SUB_HAL_POWER_CAPABILITY_SINGLEBAND_TRACKING) != 0;
    }

    public boolean hasPowerMultibandTracking() {
        return (mPowerFlags & SUB_HAL_POWER_CAPABILITY_MULTIBAND_TRACKING) != 0;
    }

    public boolean hasPowerSinglebandAcquisition() {
        return (mPowerFlags & SUB_HAL_POWER_CAPABILITY_SINGLEBAND_ACQUISITION) != 0;
    }

    public boolean hasPowerMultibandAcquisition() {
        return (mPowerFlags & SUB_HAL_POWER_CAPABILITY_MULTIBAND_ACQUISITION) != 0;
    }

    public boolean hasPowerOtherModes() {
        return (mPowerFlags & SUB_HAL_POWER_CAPABILITY_OTHER_MODES) != 0;
    }

    public List<GnssSignalType> getGnssSignalTypes() {
        return mGnssSignalTypes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GnssCapabilities)) {
            return false;
        }
        GnssCapabilities that = (GnssCapabilities) o;
        return mTopFlags == that.mTopFlags
                && mAdrCapability == that.mAdrCapability
                && mMeasurementCorrectionsFlags == that.mMeasurementCorrectionsFlags
                && mPowerFlags == that.mPowerFlags
                && mGnssSignalTypes.equals(that.mGnssSignalTypes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mTopFlags,
                mAdrCapability,
                mMeasurementCorrectionsFlags,
                mPowerFlags,
                mGnssSignalTypes);
    }

    public static final Parcelable.Creator<GnssCapabilities> CREATOR =
            new Parcelable.Creator<GnssCapabilities>() {
                @Override
                public GnssCapabilities createFromParcel(Parcel in) {
                    int topFlags = in.readInt();
                    int adrCapability = in.readInt();
                    int measurementCorrectionsFlags = in.readInt();
                    int powerFlags = in.readInt();
                    List<GnssSignalType> signalTypes =
                            in.createTypedArrayList(GnssSignalType.CREATOR);
                    return new GnssCapabilities(
                            topFlags,
                            adrCapability,
                            measurementCorrectionsFlags,
                            powerFlags,
                            signalTypes);
                }

                @Override
                public GnssCapabilities[] newArray(int size) {
                    return new GnssCapabilities[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(mTopFlags);
        parcel.writeInt(mAdrCapability);
        parcel.writeInt(mMeasurementCorrectionsFlags);
        parcel.writeInt(mPowerFlags);
        parcel.writeTypedList(mGnssSignalTypes);
    }

    @Override
    public String toString() {
        return "GnssCapabilities[topFlags=0x" + Integer.toHexString(mTopFlags)
                + ", adr=" + mAdrCapability
                + ", signalTypes=" + mGnssSignalTypes.size() + "]";
    }

    public static final class Builder {
        private int mTopFlags;
        private int mAdrCapability = CAPABILITY_UNKNOWN;
        private int mMeasurementCorrectionsFlags;
        private int mPowerFlags;
        private List<GnssSignalType> mGnssSignalTypes = Collections.emptyList();

        public Builder() {
        }

        public Builder(GnssCapabilities capabilities) {
            Objects.requireNonNull(capabilities, "capabilities must not be null");
            mTopFlags = capabilities.mTopFlags;
            mAdrCapability = capabilities.mAdrCapability;
            mMeasurementCorrectionsFlags = capabilities.mMeasurementCorrectionsFlags;
            mPowerFlags = capabilities.mPowerFlags;
            mGnssSignalTypes = capabilities.mGnssSignalTypes;
        }

        private static int setFlag(int flags, int mask, boolean capable) {
            return capable ? (flags | mask) : (flags & ~mask);
        }

        public Builder setHasScheduling(boolean capable) {
            mTopFlags = setFlag(mTopFlags, TOP_HAL_CAPABILITY_SCHEDULING, capable);
            return this;
        }

        public Builder setHasMsb(boolean capable) {
            mTopFlags = setFlag(mTopFlags, TOP_HAL_CAPABILITY_MSB, capable);
            return this;
        }

        public Builder setHasMsa(boolean capable) {
            mTopFlags = setFlag(mTopFlags, TOP_HAL_CAPABILITY_MSA, capable);
            return this;
        }

        public Builder setHasSingleShotFix(boolean capable) {
            mTopFlags = setFlag(mTopFlags, TOP_HAL_CAPABILITY_SINGLE_SHOT, capable);
            return this;
        }

        public Builder setHasOnDemandTime(boolean capable) {
            mTopFlags = setFlag(mTopFlags, TOP_HAL_CAPABILITY_ON_DEMAND_TIME, capable);
            return this;
        }

        public Builder setHasGeofencing(boolean capable) {
            mTopFlags = setFlag(mTopFlags, TOP_HAL_CAPABILITY_GEOFENCING, capable);
            return this;
        }

        public Builder setHasMeasurements(boolean capable) {
            mTopFlags = setFlag(mTopFlags, TOP_HAL_CAPABILITY_MEASUREMENTS, capable);
            return this;
        }

        @Deprecated
        public Builder setHasNavigationMessages(boolean capable) {
            mTopFlags = setFlag(mTopFlags, TOP_HAL_CAPABILITY_NAV_MESSAGES, capable);
            return this;
        }

        @Deprecated
        public Builder setHasLowPowerMode(boolean capable) {
            mTopFlags = setFlag(mTopFlags, TOP_HAL_CAPABILITY_LOW_POWER_MODE, capable);
            return this;
        }

        @Deprecated
        public Builder setHasSatelliteBlocklist(boolean capable) {
            mTopFlags = setFlag(mTopFlags, TOP_HAL_CAPABILITY_SATELLITE_BLOCKLIST, capable);
            return this;
        }

        public Builder setHasSatellitePvt(boolean capable) {
            mTopFlags = setFlag(mTopFlags, TOP_HAL_CAPABILITY_SATELLITE_PVT, capable);
            return this;
        }

        public Builder setHasMeasurementCorrections(boolean capable) {
            mTopFlags = setFlag(mTopFlags, TOP_HAL_CAPABILITY_MEASUREMENT_CORRECTIONS, capable);
            return this;
        }

        public Builder setHasAntennaInfo(boolean capable) {
            mTopFlags = setFlag(mTopFlags, TOP_HAL_CAPABILITY_ANTENNA_INFO, capable);
            return this;
        }

        public Builder setHasMeasurementCorrelationVectors(boolean capable) {
            mTopFlags = setFlag(mTopFlags, TOP_HAL_CAPABILITY_CORRELATION_VECTOR, capable);
            return this;
        }

        public Builder setHasMeasurementCorrectionsForDriving(boolean capable) {
            mTopFlags = setFlag(
                    mTopFlags, TOP_HAL_CAPABILITY_MEASUREMENT_CORRECTIONS_FOR_DRIVING, capable);
            return this;
        }

        public Builder setHasAccumulatedDeltaRange(int capable) {
            if (capable != CAPABILITY_UNKNOWN
                    && capable != CAPABILITY_SUPPORTED
                    && capable != CAPABILITY_UNSUPPORTED) {
                throw new IllegalArgumentException("Invalid ADR capability: " + capable);
            }
            mAdrCapability = capable;
            return this;
        }

        public Builder setHasMeasurementCorrectionsLosSats(boolean capable) {
            mMeasurementCorrectionsFlags = setFlag(
                    mMeasurementCorrectionsFlags,
                    SUB_HAL_MEASUREMENT_CORRECTIONS_CAPABILITY_LOS_SATS,
                    capable);
            return this;
        }

        public Builder setHasMeasurementCorrectionsExcessPathLength(boolean capable) {
            mMeasurementCorrectionsFlags = setFlag(
                    mMeasurementCorrectionsFlags,
                    SUB_HAL_MEASUREMENT_CORRECTIONS_CAPABILITY_EXCESS_PATH_LENGTH,
                    capable);
            return this;
        }

        public Builder setHasMeasurementCorrectionsReflectingPlane(boolean capable) {
            mMeasurementCorrectionsFlags = setFlag(
                    mMeasurementCorrectionsFlags,
                    SUB_HAL_MEASUREMENT_CORRECTIONS_CAPABILITY_REFLECTING_PLANE,
                    capable);
            return this;
        }

        public Builder setHasPowerTotal(boolean capable) {
            mPowerFlags = setFlag(mPowerFlags, SUB_HAL_POWER_CAPABILITY_TOTAL, capable);
            return this;
        }

        public Builder setHasPowerSinglebandTracking(boolean capable) {
            mPowerFlags = setFlag(
                    mPowerFlags, SUB_HAL_POWER_CAPABILITY_SINGLEBAND_TRACKING, capable);
            return this;
        }

        public Builder setHasPowerMultibandTracking(boolean capable) {
            mPowerFlags = setFlag(
                    mPowerFlags, SUB_HAL_POWER_CAPABILITY_MULTIBAND_TRACKING, capable);
            return this;
        }

        public Builder setHasPowerSinglebandAcquisition(boolean capable) {
            mPowerFlags = setFlag(
                    mPowerFlags, SUB_HAL_POWER_CAPABILITY_SINGLEBAND_ACQUISITION, capable);
            return this;
        }

        public Builder setHasPowerMultibandAcquisition(boolean capable) {
            mPowerFlags = setFlag(
                    mPowerFlags, SUB_HAL_POWER_CAPABILITY_MULTIBAND_ACQUISITION, capable);
            return this;
        }

        public Builder setHasPowerOtherModes(boolean capable) {
            mPowerFlags = setFlag(mPowerFlags, SUB_HAL_POWER_CAPABILITY_OTHER_MODES, capable);
            return this;
        }

        public Builder setGnssSignalTypes(List<GnssSignalType> gnssSignalTypes) {
            Objects.requireNonNull(gnssSignalTypes, "gnssSignalTypes must not be null");
            mGnssSignalTypes = new ArrayList<GnssSignalType>(gnssSignalTypes);
            return this;
        }

        public GnssCapabilities build() {
            return new GnssCapabilities(
                    mTopFlags,
                    mAdrCapability,
                    mMeasurementCorrectionsFlags,
                    mPowerFlags,
                    mGnssSignalTypes);
        }
    }
}
