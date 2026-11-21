// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.customaudience;

import android.adservices.common.AdSelectionSignals;
import android.os.Parcel;
import android.os.Parcelable;
import java.time.Instant;
import java.util.Objects;

/** Partial custom audience fields provided for scheduled server updates. */
public final class PartialCustomAudience implements Parcelable {
    private final String mName;
    private final Instant mActivationTime;
    private final Instant mExpirationTime;
    private final AdSelectionSignals mUserBiddingSignals;

    private PartialCustomAudience(
            String name,
            Instant activationTime,
            Instant expirationTime,
            AdSelectionSignals userBiddingSignals) {
        this.mName = Objects.requireNonNull(name, "name");
        this.mActivationTime = activationTime;
        this.mExpirationTime = expirationTime;
        this.mUserBiddingSignals = userBiddingSignals;
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
    public int hashCode() {
        return Objects.hash(mName, mActivationTime, mExpirationTime, mUserBiddingSignals);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PartialCustomAudience)) {
            return false;
        }
        PartialCustomAudience that = (PartialCustomAudience) o;
        return Objects.equals(mName, that.mName)
                && Objects.equals(mActivationTime, that.mActivationTime)
                && Objects.equals(mExpirationTime, that.mExpirationTime)
                && Objects.equals(mUserBiddingSignals, that.mUserBiddingSignals);
    }

    @Override
    public String toString() {
        return "PartialCustomAudience{name='"
                + mName
                + "', activationTime="
                + mActivationTime
                + ", expirationTime="
                + mExpirationTime
                + ", userBiddingSignals="
                + mUserBiddingSignals
                + "}";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Objects.requireNonNull(dest, "dest");
        dest.writeString(mName);
        CustomAudience.writeInstant(dest, mActivationTime);
        CustomAudience.writeInstant(dest, mExpirationTime);
        dest.writeTypedObject(mUserBiddingSignals, flags);
    }

    public static final class Builder {
        private final String mName;
        private Instant mActivationTime;
        private Instant mExpirationTime;
        private AdSelectionSignals mUserBiddingSignals;

        public Builder(String name) {
            this.mName = Objects.requireNonNull(name, "name");
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

        public PartialCustomAudience build() {
            return new PartialCustomAudience(
                    mName, mActivationTime, mExpirationTime, mUserBiddingSignals);
        }
    }

    public static final Parcelable.Creator<PartialCustomAudience> CREATOR =
            new Parcelable.Creator<PartialCustomAudience>() {
                @Override
                public PartialCustomAudience createFromParcel(Parcel in) {
                    Objects.requireNonNull(in, "in");
                    String name = in.readString();
                    Instant activation = CustomAudience.readInstant(in);
                    Instant expiration = CustomAudience.readInstant(in);
                    AdSelectionSignals signals = in.readTypedObject(AdSelectionSignals.CREATOR);
                    return new PartialCustomAudience(name, activation, expiration, signals);
                }

                @Override
                public PartialCustomAudience[] newArray(int size) {
                    return new PartialCustomAudience[size];
                }
            };
}
