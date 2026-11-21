// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.customaudience;

import android.adservices.common.AdData;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Represents a buyer-managed remarketing Custom Audience in Protected Audience API. */
public final class CustomAudience implements Parcelable {
    public static final int FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS = 1 << 0;

    private final AdTechIdentifier mBuyer;
    private final String mName;
    private final Instant mActivationTime;
    private final Instant mExpirationTime;
    private final Uri mDailyUpdateUri;
    private final AdSelectionSignals mUserBiddingSignals;
    private final TrustedBiddingData mTrustedBiddingData;
    private final Uri mBiddingLogicUri;
    private final List<AdData> mAds;
    private final int mAuctionServerRequestFlags;

    private CustomAudience(
            AdTechIdentifier buyer,
            String name,
            Instant activationTime,
            Instant expirationTime,
            Uri dailyUpdateUri,
            AdSelectionSignals userBiddingSignals,
            TrustedBiddingData trustedBiddingData,
            Uri biddingLogicUri,
            List<AdData> ads,
            int auctionServerRequestFlags) {
        this.mBuyer = Objects.requireNonNull(buyer, "buyer");
        this.mName = Objects.requireNonNull(name, "name");
        this.mActivationTime = activationTime;
        this.mExpirationTime = expirationTime;
        if (activationTime != null
                && expirationTime != null
                && expirationTime.isBefore(activationTime)) {
            throw new IllegalArgumentException(
                    "expirationTime must not precede activationTime");
        }
        this.mDailyUpdateUri = Objects.requireNonNull(dailyUpdateUri, "dailyUpdateUri");
        this.mUserBiddingSignals = userBiddingSignals;
        this.mTrustedBiddingData = trustedBiddingData;
        this.mBiddingLogicUri = Objects.requireNonNull(biddingLogicUri, "biddingLogicUri");
        List<AdData> copy = new ArrayList<AdData>();
        if (ads != null) {
            for (AdData ad : ads) {
                copy.add(Objects.requireNonNull(ad, "ad"));
            }
        }
        this.mAds = Collections.unmodifiableList(copy);
        this.mAuctionServerRequestFlags = auctionServerRequestFlags;
    }

    public AdTechIdentifier getBuyer() {
        return mBuyer;
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

    public Uri getDailyUpdateUri() {
        return mDailyUpdateUri;
    }

    public AdSelectionSignals getUserBiddingSignals() {
        return mUserBiddingSignals;
    }

    public TrustedBiddingData getTrustedBiddingData() {
        return mTrustedBiddingData;
    }

    public Uri getBiddingLogicUri() {
        return mBiddingLogicUri;
    }

    public List<AdData> getAds() {
        return mAds;
    }

    public int getAuctionServerRequestFlags() {
        return mAuctionServerRequestFlags;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Objects.requireNonNull(dest, "dest");
        dest.writeTypedObject(mBuyer, flags);
        dest.writeString(mName);
        writeInstant(dest, mActivationTime);
        writeInstant(dest, mExpirationTime);
        dest.writeString(mDailyUpdateUri.toString());
        dest.writeTypedObject(mUserBiddingSignals, flags);
        dest.writeTypedObject(mTrustedBiddingData, flags);
        dest.writeString(mBiddingLogicUri.toString());
        dest.writeTypedList(mAds);
        dest.writeInt(mAuctionServerRequestFlags);
    }

    static void writeInstant(Parcel dest, Instant instant) {
        if (instant == null) {
            dest.writeBoolean(false);
        } else {
            dest.writeBoolean(true);
            dest.writeLong(instant.getEpochSecond());
            dest.writeInt(instant.getNano());
        }
    }

    static Instant readInstant(Parcel in) {
        if (!in.readBoolean()) {
            return null;
        }
        long sec = in.readLong();
        int nano = in.readInt();
        return Instant.ofEpochSecond(sec, nano);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CustomAudience)) {
            return false;
        }
        CustomAudience that = (CustomAudience) o;
        return mAuctionServerRequestFlags == that.mAuctionServerRequestFlags
                && Objects.equals(mBuyer, that.mBuyer)
                && Objects.equals(mName, that.mName)
                && Objects.equals(mActivationTime, that.mActivationTime)
                && Objects.equals(mExpirationTime, that.mExpirationTime)
                && Objects.equals(mDailyUpdateUri, that.mDailyUpdateUri)
                && Objects.equals(mUserBiddingSignals, that.mUserBiddingSignals)
                && Objects.equals(mTrustedBiddingData, that.mTrustedBiddingData)
                && Objects.equals(mBiddingLogicUri, that.mBiddingLogicUri)
                && Objects.equals(mAds, that.mAds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mBuyer,
                mName,
                mActivationTime,
                mExpirationTime,
                mDailyUpdateUri,
                mUserBiddingSignals,
                mTrustedBiddingData,
                mBiddingLogicUri,
                mAds,
                Integer.valueOf(mAuctionServerRequestFlags));
    }

    @Override
    public String toString() {
        return "CustomAudience{mBuyer="
                + mBuyer
                + ", mName='"
                + mName
                + "', mActivationTime="
                + mActivationTime
                + ", mExpirationTime="
                + mExpirationTime
                + ", mDailyUpdateUri="
                + mDailyUpdateUri
                + ", mUserBiddingSignals="
                + mUserBiddingSignals
                + ", mTrustedBiddingData="
                + mTrustedBiddingData
                + ", mBiddingLogicUri="
                + mBiddingLogicUri
                + ", mAds="
                + mAds
                + ", mAuctionServerRequestFlags="
                + mAuctionServerRequestFlags
                + "}";
    }

    public static final class Builder {
        private AdTechIdentifier mBuyer;
        private String mName;
        private Instant mActivationTime;
        private Instant mExpirationTime;
        private Uri mDailyUpdateUri;
        private AdSelectionSignals mUserBiddingSignals;
        private TrustedBiddingData mTrustedBiddingData;
        private Uri mBiddingLogicUri;
        private List<AdData> mAds = Collections.emptyList();
        private int mAuctionServerRequestFlags;

        public Builder() {
        }

        public Builder setBuyer(AdTechIdentifier buyer) {
            this.mBuyer = Objects.requireNonNull(buyer, "buyer");
            return this;
        }

        public Builder setName(String name) {
            this.mName = Objects.requireNonNull(name, "name");
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

        public Builder setDailyUpdateUri(Uri dailyUpdateUri) {
            this.mDailyUpdateUri = Objects.requireNonNull(dailyUpdateUri, "dailyUpdateUri");
            return this;
        }

        public Builder setUserBiddingSignals(AdSelectionSignals userBiddingSignals) {
            this.mUserBiddingSignals = userBiddingSignals;
            return this;
        }

        public Builder setTrustedBiddingData(TrustedBiddingData trustedBiddingData) {
            this.mTrustedBiddingData = trustedBiddingData;
            return this;
        }

        public Builder setBiddingLogicUri(Uri biddingLogicUri) {
            this.mBiddingLogicUri = Objects.requireNonNull(biddingLogicUri, "biddingLogicUri");
            return this;
        }

        public Builder setAds(List<AdData> ads) {
            this.mAds = ads == null ? Collections.<AdData>emptyList() : new ArrayList<AdData>(ads);
            return this;
        }

        public Builder setAuctionServerRequestFlags(int auctionServerRequestFlags) {
            this.mAuctionServerRequestFlags = auctionServerRequestFlags;
            return this;
        }

        public CustomAudience build() {
            return new CustomAudience(
                    mBuyer,
                    mName,
                    mActivationTime,
                    mExpirationTime,
                    mDailyUpdateUri,
                    mUserBiddingSignals,
                    mTrustedBiddingData,
                    mBiddingLogicUri,
                    mAds,
                    mAuctionServerRequestFlags);
        }
    }

    public static final Parcelable.Creator<CustomAudience> CREATOR =
            new Parcelable.Creator<CustomAudience>() {
                @Override
                public CustomAudience createFromParcel(Parcel in) {
                    Objects.requireNonNull(in, "in");
                    AdTechIdentifier buyer = in.readTypedObject(AdTechIdentifier.CREATOR);
                    String name = in.readString();
                    Instant activation = readInstant(in);
                    Instant expiration = readInstant(in);
                    Uri dailyUri = Uri.parse(in.readString());
                    AdSelectionSignals signals = in.readTypedObject(AdSelectionSignals.CREATOR);
                    TrustedBiddingData biddingData =
                            in.readTypedObject(TrustedBiddingData.CREATOR);
                    Uri biddingUri = Uri.parse(in.readString());
                    List<AdData> ads = in.createTypedArrayList(AdData.CREATOR);
                    int flags = in.readInt();
                    return new CustomAudience(
                            buyer,
                            name,
                            activation,
                            expiration,
                            dailyUri,
                            signals,
                            biddingData,
                            biddingUri,
                            ads,
                            flags);
                }

                @Override
                public CustomAudience[] newArray(int size) {
                    return new CustomAudience[size];
                }
            };
}
