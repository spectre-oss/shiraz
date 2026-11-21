// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.customaudience;

import android.adservices.common.AdSelectionSignals;
import android.net.Uri;
import java.time.Instant;
import java.util.Objects;

/** Request payload for {@link CustomAudienceManager#fetchAndJoinCustomAudience}. */
public final class FetchAndJoinCustomAudienceRequest {
    private final Uri mFetchUri;
    private final String mName;
    private final Instant mActivationTime;
    private final Instant mExpirationTime;
    private final AdSelectionSignals mUserBiddingSignals;

    private FetchAndJoinCustomAudienceRequest(
            Uri fetchUri,
            String name,
            Instant activationTime,
            Instant expirationTime,
            AdSelectionSignals userBiddingSignals) {
        this.mFetchUri = Objects.requireNonNull(fetchUri, "fetchUri");
        this.mName = name;
        this.mActivationTime = activationTime;
        this.mExpirationTime = expirationTime;
        this.mUserBiddingSignals = userBiddingSignals;
    }

    public Uri getFetchUri() {
        return mFetchUri;
    }

    public String getName() {
        return mName;
    }

    public Instant getActivationTime() {
        return mActivationTime;
    }

    public Instant getExpirationTime() {
        return mExpirationTime;
    }

    public AdSelectionSignals getUserBiddingSignals() {
        return mUserBiddingSignals;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FetchAndJoinCustomAudienceRequest)) {
            return false;
        }
        FetchAndJoinCustomAudienceRequest that = (FetchAndJoinCustomAudienceRequest) o;
        return Objects.equals(mFetchUri, that.mFetchUri)
                && Objects.equals(mName, that.mName)
                && Objects.equals(mActivationTime, that.mActivationTime)
                && Objects.equals(mExpirationTime, that.mExpirationTime)
                && Objects.equals(mUserBiddingSignals, that.mUserBiddingSignals);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mFetchUri, mName, mActivationTime, mExpirationTime, mUserBiddingSignals);
    }

    @Override
    public String toString() {
        return "FetchAndJoinCustomAudienceRequest{fetchUri="
                + mFetchUri
                + ", name="
                + mName
                + ", activationTime="
                + mActivationTime
                + ", expirationTime="
                + mExpirationTime
                + ", userBiddingSignals="
                + mUserBiddingSignals
                + "}";
    }

    public static final class Builder {
        private Uri mFetchUri;
        private String mName;
        private Instant mActivationTime;
        private Instant mExpirationTime;
        private AdSelectionSignals mUserBiddingSignals;

        public Builder(Uri fetchUri) {
            this.mFetchUri = Objects.requireNonNull(fetchUri, "fetchUri");
        }

        public Builder setFetchUri(Uri fetchUri) {
            this.mFetchUri = Objects.requireNonNull(fetchUri, "fetchUri");
            return this;
        }

        public Builder setName(String name) {
            this.mName = name;
            return this;
        }

        public Builder setActivationTime(Instant activationTime) {
            this.mActivationTime = activationTime;
            return this;
        }

        public Builder setExpirationTime(Instant expirationTime) {
            this.mExpirationTime = expirationTime;
            return this;
        }

        public Builder setUserBiddingSignals(AdSelectionSignals userBiddingSignals) {
            this.mUserBiddingSignals = userBiddingSignals;
            return this;
        }

        public FetchAndJoinCustomAudienceRequest build() {
            return new FetchAndJoinCustomAudienceRequest(
                    mFetchUri, mName, mActivationTime, mExpirationTime, mUserBiddingSignals);
        }
    }
}
