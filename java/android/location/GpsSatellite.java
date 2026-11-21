// SPDX-License-Identifier: GPL-3.0-only
package android.location;

@Deprecated
public final class GpsSatellite {
    int mPrn;
    float mSnr;
    float mElevation;
    float mAzimuth;
    boolean mHasEphemeris;
    boolean mHasAlmanac;
    boolean mUsedInFix;

    GpsSatellite(int prn) {
        mPrn = prn;
    }

    public int getPrn() {
        return mPrn;
    }

    public float getSnr() {
        return mSnr;
    }

    public float getElevation() {
        return mElevation;
    }

    public float getAzimuth() {
        return mAzimuth;
    }

    public boolean hasEphemeris() {
        return mHasEphemeris;
    }

    public boolean hasAlmanac() {
        return mHasAlmanac;
    }

    public boolean usedInFix() {
        return mUsedInFix;
    }
}
