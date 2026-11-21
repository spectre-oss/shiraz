// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.adid;

import java.util.Objects;

/** Advertising ID value and limit-ad-tracking preference. */
public class AdId {
    public static final String ZERO_OUT = "00000000-0000-0000-0000-000000000000";

    private final String mAdId;
    private final boolean mLimitAdTrackingEnabled;

    public AdId(String adId, boolean limitAdTrackingEnabled) {
        this.mAdId = Objects.requireNonNull(adId, "adId");
        this.mLimitAdTrackingEnabled = limitAdTrackingEnabled;
    }

    public String getAdId() {
        return mAdId;
    }

    public boolean isLimitAdTrackingEnabled() {
        return mLimitAdTrackingEnabled;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AdId)) {
            return false;
        }
        AdId that = (AdId) o;
        return mLimitAdTrackingEnabled == that.mLimitAdTrackingEnabled
                && Objects.equals(mAdId, that.mAdId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mAdId, Boolean.valueOf(mLimitAdTrackingEnabled));
    }

    @Override
    public String toString() {
        return "AdId{mAdId='"
                + mAdId
                + "', mLimitAdTrackingEnabled="
                + mLimitAdTrackingEnabled
                + "}";
    }
}
