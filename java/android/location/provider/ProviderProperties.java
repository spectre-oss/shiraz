// SPDX-License-Identifier: GPL-3.0-only
package android.location.provider;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class ProviderProperties implements Parcelable {
    public static final int POWER_USAGE_LOW = 1;
    public static final int POWER_USAGE_MEDIUM = 2;
    public static final int POWER_USAGE_HIGH = 3;

    public static final int ACCURACY_FINE = 1;
    public static final int ACCURACY_COARSE = 2;

    private final boolean mHasNetworkRequirement;
    private final boolean mHasSatelliteRequirement;
    private final boolean mHasCellRequirement;
    private final boolean mHasMonetaryCost;
    private final boolean mHasAltitudeSupport;
    private final boolean mHasSpeedSupport;
    private final boolean mHasBearingSupport;
    private final int mPowerUsage;
    private final int mAccuracy;

    private ProviderProperties(
            boolean hasNetworkRequirement,
            boolean hasSatelliteRequirement,
            boolean hasCellRequirement,
            boolean hasMonetaryCost,
            boolean hasAltitudeSupport,
            boolean hasSpeedSupport,
            boolean hasBearingSupport,
            int powerUsage,
            int accuracy) {
        mHasNetworkRequirement = hasNetworkRequirement;
        mHasSatelliteRequirement = hasSatelliteRequirement;
        mHasCellRequirement = hasCellRequirement;
        mHasMonetaryCost = hasMonetaryCost;
        mHasAltitudeSupport = hasAltitudeSupport;
        mHasSpeedSupport = hasSpeedSupport;
        mHasBearingSupport = hasBearingSupport;
        mPowerUsage = powerUsage;
        mAccuracy = accuracy;
    }

    public boolean hasNetworkRequirement() {
        return mHasNetworkRequirement;
    }

    public boolean hasSatelliteRequirement() {
        return mHasSatelliteRequirement;
    }

    public boolean hasCellRequirement() {
        return mHasCellRequirement;
    }

    public boolean hasMonetaryCost() {
        return mHasMonetaryCost;
    }

    public boolean hasAltitudeSupport() {
        return mHasAltitudeSupport;
    }

    public boolean hasSpeedSupport() {
        return mHasSpeedSupport;
    }

    public boolean hasBearingSupport() {
        return mHasBearingSupport;
    }

    public int getPowerUsage() {
        return mPowerUsage;
    }

    public int getAccuracy() {
        return mAccuracy;
    }

    public static final Parcelable.Creator<ProviderProperties> CREATOR =
            new Parcelable.Creator<ProviderProperties>() {
                @Override
                public ProviderProperties createFromParcel(Parcel in) {
                    return new ProviderProperties(
                            in.readBoolean(),
                            in.readBoolean(),
                            in.readBoolean(),
                            in.readBoolean(),
                            in.readBoolean(),
                            in.readBoolean(),
                            in.readBoolean(),
                            in.readInt(),
                            in.readInt());
                }

                @Override
                public ProviderProperties[] newArray(int size) {
                    return new ProviderProperties[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeBoolean(mHasNetworkRequirement);
        parcel.writeBoolean(mHasSatelliteRequirement);
        parcel.writeBoolean(mHasCellRequirement);
        parcel.writeBoolean(mHasMonetaryCost);
        parcel.writeBoolean(mHasAltitudeSupport);
        parcel.writeBoolean(mHasSpeedSupport);
        parcel.writeBoolean(mHasBearingSupport);
        parcel.writeInt(mPowerUsage);
        parcel.writeInt(mAccuracy);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProviderProperties)) {
            return false;
        }
        ProviderProperties that = (ProviderProperties) o;
        return mHasNetworkRequirement == that.mHasNetworkRequirement
                && mHasSatelliteRequirement == that.mHasSatelliteRequirement
                && mHasCellRequirement == that.mHasCellRequirement
                && mHasMonetaryCost == that.mHasMonetaryCost
                && mHasAltitudeSupport == that.mHasAltitudeSupport
                && mHasSpeedSupport == that.mHasSpeedSupport
                && mHasBearingSupport == that.mHasBearingSupport
                && mPowerUsage == that.mPowerUsage
                && mAccuracy == that.mAccuracy;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mHasNetworkRequirement,
                mHasSatelliteRequirement,
                mHasCellRequirement,
                mHasMonetaryCost,
                mHasAltitudeSupport,
                mHasSpeedSupport,
                mHasBearingSupport,
                mPowerUsage,
                mAccuracy);
    }

    @Override
    public String toString() {
        return "ProviderProperties[powerUsage=" + mPowerUsage
                + ", accuracy=" + mAccuracy + "]";
    }

    public static final class Builder {
        private boolean mHasNetworkRequirement = false;
        private boolean mHasSatelliteRequirement = false;
        private boolean mHasCellRequirement = false;
        private boolean mHasMonetaryCost = false;
        private boolean mHasAltitudeSupport = false;
        private boolean mHasSpeedSupport = false;
        private boolean mHasBearingSupport = false;
        private int mPowerUsage = POWER_USAGE_HIGH;
        private int mAccuracy = ACCURACY_COARSE;

        public Builder() {
        }

        public Builder(ProviderProperties providerProperties) {
            Objects.requireNonNull(providerProperties, "providerProperties must not be null");
            mHasNetworkRequirement = providerProperties.mHasNetworkRequirement;
            mHasSatelliteRequirement = providerProperties.mHasSatelliteRequirement;
            mHasCellRequirement = providerProperties.mHasCellRequirement;
            mHasMonetaryCost = providerProperties.mHasMonetaryCost;
            mHasAltitudeSupport = providerProperties.mHasAltitudeSupport;
            mHasSpeedSupport = providerProperties.mHasSpeedSupport;
            mHasBearingSupport = providerProperties.mHasBearingSupport;
            mPowerUsage = providerProperties.mPowerUsage;
            mAccuracy = providerProperties.mAccuracy;
        }

        public Builder setHasNetworkRequirement(boolean requiresNetwork) {
            mHasNetworkRequirement = requiresNetwork;
            return this;
        }

        public Builder setHasSatelliteRequirement(boolean requiresSatellite) {
            mHasSatelliteRequirement = requiresSatellite;
            return this;
        }

        public Builder setHasCellRequirement(boolean requiresCell) {
            mHasCellRequirement = requiresCell;
            return this;
        }

        public Builder setHasMonetaryCost(boolean monetaryCost) {
            mHasMonetaryCost = monetaryCost;
            return this;
        }

        public Builder setHasAltitudeSupport(boolean supportsAltitude) {
            mHasAltitudeSupport = supportsAltitude;
            return this;
        }

        public Builder setHasSpeedSupport(boolean supportsSpeed) {
            mHasSpeedSupport = supportsSpeed;
            return this;
        }

        public Builder setHasBearingSupport(boolean supportsBearing) {
            mHasBearingSupport = supportsBearing;
            return this;
        }

        public Builder setPowerUsage(int powerUsage) {
            if (powerUsage < POWER_USAGE_LOW || powerUsage > POWER_USAGE_HIGH) {
                throw new IllegalArgumentException("Invalid powerUsage: " + powerUsage);
            }
            mPowerUsage = powerUsage;
            return this;
        }

        public Builder setAccuracy(int accuracy) {
            if (accuracy < ACCURACY_FINE || accuracy > ACCURACY_COARSE) {
                throw new IllegalArgumentException("Invalid accuracy: " + accuracy);
            }
            mAccuracy = accuracy;
            return this;
        }

        public ProviderProperties build() {
            return new ProviderProperties(
                    mHasNetworkRequirement,
                    mHasSatelliteRequirement,
                    mHasCellRequirement,
                    mHasMonetaryCost,
                    mHasAltitudeSupport,
                    mHasSpeedSupport,
                    mHasBearingSupport,
                    mPowerUsage,
                    mAccuracy);
        }
    }
}
