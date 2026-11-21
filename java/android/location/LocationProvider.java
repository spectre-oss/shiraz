// SPDX-License-Identifier: GPL-3.0-only
package android.location;

import android.location.provider.ProviderProperties;

@Deprecated
public class LocationProvider {
    @Deprecated
    public static final int OUT_OF_SERVICE = 0;
    @Deprecated
    public static final int TEMPORARILY_UNAVAILABLE = 1;
    @Deprecated
    public static final int AVAILABLE = 2;

    private final String mName;
    private final ProviderProperties mProperties;

    LocationProvider(String name, ProviderProperties properties) {
        mName = name;
        mProperties = properties;
    }

    public String getName() {
        return mName;
    }

    public boolean meetsCriteria(Criteria criteria) {
        if (criteria == null) {
            return false;
        }
        if (criteria.getAccuracy() != Criteria.NO_REQUIREMENT
                && criteria.getAccuracy() < mProperties.getAccuracy()) {
            return false;
        }
        if (criteria.getPowerRequirement() != Criteria.NO_REQUIREMENT
                && criteria.getPowerRequirement() < mProperties.getPowerUsage()) {
            return false;
        }
        if (criteria.isAltitudeRequired() && !mProperties.hasAltitudeSupport()) {
            return false;
        }
        if (criteria.isSpeedRequired() && !mProperties.hasSpeedSupport()) {
            return false;
        }
        if (criteria.isBearingRequired() && !mProperties.hasBearingSupport()) {
            return false;
        }
        if (!criteria.isCostAllowed() && mProperties.hasMonetaryCost()) {
            return false;
        }
        return true;
    }

    public boolean requiresNetwork() {
        return mProperties.hasNetworkRequirement();
    }

    public boolean requiresSatellite() {
        return mProperties.hasSatelliteRequirement();
    }

    public boolean requiresCell() {
        return mProperties.hasCellRequirement();
    }

    public boolean hasMonetaryCost() {
        return mProperties.hasMonetaryCost();
    }

    public boolean supportsAltitude() {
        return mProperties.hasAltitudeSupport();
    }

    public boolean supportsSpeed() {
        return mProperties.hasSpeedSupport();
    }

    public boolean supportsBearing() {
        return mProperties.hasBearingSupport();
    }

    public int getPowerRequirement() {
        return mProperties.getPowerUsage();
    }

    public int getAccuracy() {
        return mProperties.getAccuracy();
    }
}
