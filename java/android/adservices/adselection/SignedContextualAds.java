// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.adselection;

import android.adservices.common.AdTechIdentifier;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Signed bundle of contextual ads with bids from a buyer ad tech. */
public final class SignedContextualAds implements Parcelable {
    private final AdTechIdentifier mBuyer;
    private final Uri mDecisionLogicUri;
    private final List<AdWithBid> mAdsWithBid;
    private final byte[] mSignature;

    private SignedContextualAds(
            AdTechIdentifier buyer,
            Uri decisionLogicUri,
            List<AdWithBid> adsWithBid,
            byte[] signature) {
        this.mBuyer = Objects.requireNonNull(buyer, "buyer");
        this.mDecisionLogicUri = Objects.requireNonNull(decisionLogicUri, "decisionLogicUri");
        Objects.requireNonNull(adsWithBid, "adsWithBid");
        List<AdWithBid> copy = new ArrayList<AdWithBid>(adsWithBid.size());
        for (AdWithBid item : adsWithBid) {
            copy.add(Objects.requireNonNull(item, "adWithBid"));
        }
        this.mAdsWithBid = Collections.unmodifiableList(copy);
        Objects.requireNonNull(signature, "signature");
        this.mSignature = signature.clone();
    }

    public AdTechIdentifier getBuyer() {
        return mBuyer;
    }

    public Uri getDecisionLogicUri() {
        return mDecisionLogicUri;
    }

    public List<AdWithBid> getAdsWithBid() {
        return mAdsWithBid;
    }

    public byte[] getSignature() {
        return mSignature.clone();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Objects.requireNonNull(dest, "dest");
        dest.writeTypedObject(mBuyer, flags);
        dest.writeString(mDecisionLogicUri.toString());
        dest.writeTypedList(mAdsWithBid);
        dest.writeByteArray(mSignature);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SignedContextualAds)) {
            return false;
        }
        SignedContextualAds that = (SignedContextualAds) o;
        return Objects.equals(mBuyer, that.mBuyer)
                && Objects.equals(mDecisionLogicUri, that.mDecisionLogicUri)
                && Objects.equals(mAdsWithBid, that.mAdsWithBid)
                && Arrays.equals(mSignature, that.mSignature);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mBuyer,
                mDecisionLogicUri,
                mAdsWithBid,
                Integer.valueOf(Arrays.hashCode(mSignature)));
    }

    @Override
    public String toString() {
        return "SignedContextualAds{mBuyer="
                + mBuyer
                + ", mDecisionLogicUri="
                + mDecisionLogicUri
                + ", mAdsWithBid="
                + mAdsWithBid
                + ", mSignatureLength="
                + mSignature.length
                + "}";
    }

    public static final class Builder {
        private AdTechIdentifier mBuyer;
        private Uri mDecisionLogicUri;
        private List<AdWithBid> mAdsWithBid;
        private byte[] mSignature;

        public Builder() {
        }

        public Builder(SignedContextualAds signedContextualAds) {
            Objects.requireNonNull(signedContextualAds, "signedContextualAds");
            this.mBuyer = signedContextualAds.mBuyer;
            this.mDecisionLogicUri = signedContextualAds.mDecisionLogicUri;
            this.mAdsWithBid = new ArrayList<AdWithBid>(signedContextualAds.mAdsWithBid);
            this.mSignature = signedContextualAds.mSignature.clone();
        }

        public Builder setBuyer(AdTechIdentifier buyer) {
            this.mBuyer = Objects.requireNonNull(buyer, "buyer");
            return this;
        }

        public Builder setDecisionLogicUri(Uri decisionLogicUri) {
            this.mDecisionLogicUri =
                    Objects.requireNonNull(decisionLogicUri, "decisionLogicUri");
            return this;
        }

        public Builder setAdsWithBid(List<AdWithBid> adsWithBid) {
            Objects.requireNonNull(adsWithBid, "adsWithBid");
            this.mAdsWithBid = new ArrayList<AdWithBid>(adsWithBid);
            return this;
        }

        public Builder setSignature(byte[] signature) {
            Objects.requireNonNull(signature, "signature");
            this.mSignature = signature.clone();
            return this;
        }

        public SignedContextualAds build() {
            return new SignedContextualAds(
                    mBuyer, mDecisionLogicUri, mAdsWithBid, mSignature);
        }
    }

    public static final Parcelable.Creator<SignedContextualAds> CREATOR =
            new Parcelable.Creator<SignedContextualAds>() {
                @Override
                public SignedContextualAds createFromParcel(Parcel in) {
                    Objects.requireNonNull(in, "in");
                    AdTechIdentifier buyer = in.readTypedObject(AdTechIdentifier.CREATOR);
                    Uri uri = Uri.parse(in.readString());
                    List<AdWithBid> ads = in.createTypedArrayList(AdWithBid.CREATOR);
                    byte[] sig = in.createByteArray();
                    return new SignedContextualAds(
                            buyer,
                            uri,
                            ads == null ? Collections.<AdWithBid>emptyList() : ads,
                            sig == null ? new byte[0] : sig);
                }

                @Override
                public SignedContextualAds[] newArray(int size) {
                    return new SignedContextualAds[size];
                }
            };
}
