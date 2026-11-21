// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.adselection;

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Configuration for an on-device Protected Audience ad selection auction. */
public final class AdSelectionConfig implements Parcelable {
    private final AdTechIdentifier mSeller;
    private final Uri mDecisionLogicUri;
    private final List<AdTechIdentifier> mCustomAudienceBuyers;
    private final AdSelectionSignals mAdSelectionSignals;
    private final AdSelectionSignals mSellerSignals;
    private final Map<AdTechIdentifier, AdSelectionSignals> mPerBuyerSignals;
    private final Map<AdTechIdentifier, SignedContextualAds> mPerBuyerSignedContextualAds;
    private final Uri mTrustedScoringSignalsUri;

    private AdSelectionConfig(
            AdTechIdentifier seller,
            Uri decisionLogicUri,
            List<AdTechIdentifier> customAudienceBuyers,
            AdSelectionSignals adSelectionSignals,
            AdSelectionSignals sellerSignals,
            Map<AdTechIdentifier, AdSelectionSignals> perBuyerSignals,
            Map<AdTechIdentifier, SignedContextualAds> perBuyerSignedContextualAds,
            Uri trustedScoringSignalsUri) {
        this.mSeller = Objects.requireNonNull(seller, "seller");
        this.mDecisionLogicUri = Objects.requireNonNull(decisionLogicUri, "decisionLogicUri");
        Objects.requireNonNull(customAudienceBuyers, "customAudienceBuyers");
        List<AdTechIdentifier> buyers =
                new ArrayList<AdTechIdentifier>(customAudienceBuyers.size());
        for (AdTechIdentifier b : customAudienceBuyers) {
            buyers.add(Objects.requireNonNull(b, "buyer"));
        }
        this.mCustomAudienceBuyers = Collections.unmodifiableList(buyers);
        this.mAdSelectionSignals =
                adSelectionSignals == null ? AdSelectionSignals.EMPTY : adSelectionSignals;
        this.mSellerSignals = sellerSignals == null ? AdSelectionSignals.EMPTY : sellerSignals;
        Map<AdTechIdentifier, AdSelectionSignals> pbs =
                new LinkedHashMap<AdTechIdentifier, AdSelectionSignals>();
        if (perBuyerSignals != null) {
            for (Map.Entry<AdTechIdentifier, AdSelectionSignals> e :
                    perBuyerSignals.entrySet()) {
                pbs.put(
                        Objects.requireNonNull(e.getKey(), "buyer"),
                        Objects.requireNonNull(e.getValue(), "signals"));
            }
        }
        this.mPerBuyerSignals = Collections.unmodifiableMap(pbs);
        Map<AdTechIdentifier, SignedContextualAds> pbsca =
                new LinkedHashMap<AdTechIdentifier, SignedContextualAds>();
        if (perBuyerSignedContextualAds != null) {
            for (Map.Entry<AdTechIdentifier, SignedContextualAds> e :
                    perBuyerSignedContextualAds.entrySet()) {
                pbsca.put(
                        Objects.requireNonNull(e.getKey(), "buyer"),
                        Objects.requireNonNull(e.getValue(), "signedContextualAds"));
            }
        }
        this.mPerBuyerSignedContextualAds = Collections.unmodifiableMap(pbsca);
        this.mTrustedScoringSignalsUri =
                Objects.requireNonNull(trustedScoringSignalsUri, "trustedScoringSignalsUri");
    }

    public AdTechIdentifier getSeller() {
        return mSeller;
    }

    public Uri getDecisionLogicUri() {
        return mDecisionLogicUri;
    }

    public List<AdTechIdentifier> getCustomAudienceBuyers() {
        return mCustomAudienceBuyers;
    }

    public AdSelectionSignals getAdSelectionSignals() {
        return mAdSelectionSignals;
    }

    public AdSelectionSignals getSellerSignals() {
        return mSellerSignals;
    }

    public Map<AdTechIdentifier, AdSelectionSignals> getPerBuyerSignals() {
        return mPerBuyerSignals;
    }

    public Map<AdTechIdentifier, SignedContextualAds> getPerBuyerSignedContextualAds() {
        return mPerBuyerSignedContextualAds;
    }

    public Uri getTrustedScoringSignalsUri() {
        return mTrustedScoringSignalsUri;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Objects.requireNonNull(dest, "dest");
        dest.writeTypedObject(mSeller, flags);
        dest.writeString(mDecisionLogicUri.toString());
        dest.writeTypedList(mCustomAudienceBuyers);
        dest.writeTypedObject(mAdSelectionSignals, flags);
        dest.writeTypedObject(mSellerSignals, flags);
        dest.writeInt(mPerBuyerSignals.size());
        for (Map.Entry<AdTechIdentifier, AdSelectionSignals> e : mPerBuyerSignals.entrySet()) {
            dest.writeTypedObject(e.getKey(), flags);
            dest.writeTypedObject(e.getValue(), flags);
        }
        dest.writeInt(mPerBuyerSignedContextualAds.size());
        for (Map.Entry<AdTechIdentifier, SignedContextualAds> e :
                mPerBuyerSignedContextualAds.entrySet()) {
            dest.writeTypedObject(e.getKey(), flags);
            dest.writeTypedObject(e.getValue(), flags);
        }
        dest.writeString(mTrustedScoringSignalsUri.toString());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AdSelectionConfig)) {
            return false;
        }
        AdSelectionConfig that = (AdSelectionConfig) o;
        return Objects.equals(mSeller, that.mSeller)
                && Objects.equals(mDecisionLogicUri, that.mDecisionLogicUri)
                && Objects.equals(mCustomAudienceBuyers, that.mCustomAudienceBuyers)
                && Objects.equals(mAdSelectionSignals, that.mAdSelectionSignals)
                && Objects.equals(mSellerSignals, that.mSellerSignals)
                && Objects.equals(mPerBuyerSignals, that.mPerBuyerSignals)
                && Objects.equals(
                        mPerBuyerSignedContextualAds, that.mPerBuyerSignedContextualAds)
                && Objects.equals(mTrustedScoringSignalsUri, that.mTrustedScoringSignalsUri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mSeller,
                mDecisionLogicUri,
                mCustomAudienceBuyers,
                mAdSelectionSignals,
                mSellerSignals,
                mPerBuyerSignals,
                mPerBuyerSignedContextualAds,
                mTrustedScoringSignalsUri);
    }

    public static final class Builder {
        private AdTechIdentifier mSeller;
        private Uri mDecisionLogicUri;
        private List<AdTechIdentifier> mCustomAudienceBuyers = Collections.emptyList();
        private AdSelectionSignals mAdSelectionSignals = AdSelectionSignals.EMPTY;
        private AdSelectionSignals mSellerSignals = AdSelectionSignals.EMPTY;
        private Map<AdTechIdentifier, AdSelectionSignals> mPerBuyerSignals =
                Collections.emptyMap();
        private Map<AdTechIdentifier, SignedContextualAds> mPerBuyerSignedContextualAds =
                Collections.emptyMap();
        private Uri mTrustedScoringSignalsUri;

        public Builder() {
        }

        public Builder setSeller(AdTechIdentifier seller) {
            this.mSeller = Objects.requireNonNull(seller, "seller");
            return this;
        }

        public Builder setDecisionLogicUri(Uri decisionLogicUri) {
            this.mDecisionLogicUri =
                    Objects.requireNonNull(decisionLogicUri, "decisionLogicUri");
            return this;
        }

        public Builder setCustomAudienceBuyers(List<AdTechIdentifier> customAudienceBuyers) {
            Objects.requireNonNull(customAudienceBuyers, "customAudienceBuyers");
            this.mCustomAudienceBuyers = new ArrayList<AdTechIdentifier>(customAudienceBuyers);
            return this;
        }

        public Builder setAdSelectionSignals(AdSelectionSignals adSelectionSignals) {
            this.mAdSelectionSignals =
                    Objects.requireNonNull(adSelectionSignals, "adSelectionSignals");
            return this;
        }

        public Builder setSellerSignals(AdSelectionSignals sellerSignals) {
            this.mSellerSignals = Objects.requireNonNull(sellerSignals, "sellerSignals");
            return this;
        }

        public Builder setPerBuyerSignals(
                Map<AdTechIdentifier, AdSelectionSignals> perBuyerSignals) {
            Objects.requireNonNull(perBuyerSignals, "perBuyerSignals");
            this.mPerBuyerSignals =
                    new LinkedHashMap<AdTechIdentifier, AdSelectionSignals>(perBuyerSignals);
            return this;
        }

        public Builder setPerBuyerSignedContextualAds(
                Map<AdTechIdentifier, SignedContextualAds> perBuyerSignedContextualAds) {
            Objects.requireNonNull(
                    perBuyerSignedContextualAds, "perBuyerSignedContextualAds");
            this.mPerBuyerSignedContextualAds =
                    new LinkedHashMap<AdTechIdentifier, SignedContextualAds>(
                            perBuyerSignedContextualAds);
            return this;
        }

        public Builder setTrustedScoringSignalsUri(Uri trustedScoringSignalsUri) {
            this.mTrustedScoringSignalsUri =
                    Objects.requireNonNull(
                            trustedScoringSignalsUri, "trustedScoringSignalsUri");
            return this;
        }

        public AdSelectionConfig build() {
            return new AdSelectionConfig(
                    mSeller,
                    mDecisionLogicUri,
                    mCustomAudienceBuyers,
                    mAdSelectionSignals,
                    mSellerSignals,
                    mPerBuyerSignals,
                    mPerBuyerSignedContextualAds,
                    mTrustedScoringSignalsUri);
        }
    }

    public static final Parcelable.Creator<AdSelectionConfig> CREATOR =
            new Parcelable.Creator<AdSelectionConfig>() {
                @Override
                public AdSelectionConfig createFromParcel(Parcel in) {
                    Objects.requireNonNull(in, "in");
                    AdTechIdentifier seller = in.readTypedObject(AdTechIdentifier.CREATOR);
                    Uri decisionUri = Uri.parse(in.readString());
                    List<AdTechIdentifier> buyers =
                            in.createTypedArrayList(AdTechIdentifier.CREATOR);
                    AdSelectionSignals selSignals =
                            in.readTypedObject(AdSelectionSignals.CREATOR);
                    AdSelectionSignals sellerSignals =
                            in.readTypedObject(AdSelectionSignals.CREATOR);
                    int pbsCount = in.readInt();
                    Map<AdTechIdentifier, AdSelectionSignals> pbs =
                            new LinkedHashMap<AdTechIdentifier, AdSelectionSignals>(pbsCount);
                    for (int i = 0; i < pbsCount; i++) {
                        pbs.put(
                                in.readTypedObject(AdTechIdentifier.CREATOR),
                                in.readTypedObject(AdSelectionSignals.CREATOR));
                    }
                    int scaCount = in.readInt();
                    Map<AdTechIdentifier, SignedContextualAds> pbsca =
                            new LinkedHashMap<AdTechIdentifier, SignedContextualAds>(scaCount);
                    for (int i = 0; i < scaCount; i++) {
                        pbsca.put(
                                in.readTypedObject(AdTechIdentifier.CREATOR),
                                in.readTypedObject(SignedContextualAds.CREATOR));
                    }
                    Uri scoringUri = Uri.parse(in.readString());
                    return new AdSelectionConfig(
                            seller,
                            decisionUri,
                            buyers == null ? Collections.<AdTechIdentifier>emptyList() : buyers,
                            selSignals,
                            sellerSignals,
                            pbs,
                            pbsca,
                            scoringUri);
                }

                @Override
                public AdSelectionConfig[] newArray(int size) {
                    return new AdSelectionConfig[size];
                }
            };
}
