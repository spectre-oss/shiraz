// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.customaudience;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Trusted key-value server URI and lookup keys for custom audience bidding. */
public final class TrustedBiddingData implements Parcelable {
    private final Uri mTrustedBiddingUri;
    private final List<String> mTrustedBiddingKeys;

    private TrustedBiddingData(Uri trustedBiddingUri, List<String> trustedBiddingKeys) {
        this.mTrustedBiddingUri = Objects.requireNonNull(trustedBiddingUri, "trustedBiddingUri");
        Objects.requireNonNull(trustedBiddingKeys, "trustedBiddingKeys");
        List<String> copy = new ArrayList<String>(trustedBiddingKeys.size());
        for (String k : trustedBiddingKeys) {
            copy.add(Objects.requireNonNull(k, "trustedBiddingKey"));
        }
        this.mTrustedBiddingKeys = Collections.unmodifiableList(copy);
    }

    public Uri getTrustedBiddingUri() {
        return mTrustedBiddingUri;
    }

    public List<String> getTrustedBiddingKeys() {
        return mTrustedBiddingKeys;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Objects.requireNonNull(dest, "dest");
        dest.writeString(mTrustedBiddingUri.toString());
        dest.writeInt(mTrustedBiddingKeys.size());
        for (String key : mTrustedBiddingKeys) {
            dest.writeString(key);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TrustedBiddingData)) {
            return false;
        }
        TrustedBiddingData that = (TrustedBiddingData) o;
        return Objects.equals(mTrustedBiddingUri, that.mTrustedBiddingUri)
                && Objects.equals(mTrustedBiddingKeys, that.mTrustedBiddingKeys);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mTrustedBiddingUri, mTrustedBiddingKeys);
    }

    public static final class Builder {
        private Uri mTrustedBiddingUri;
        private List<String> mTrustedBiddingKeys;

        public Builder() {
        }

        public Builder setTrustedBiddingUri(Uri trustedBiddingUri) {
            this.mTrustedBiddingUri =
                    Objects.requireNonNull(trustedBiddingUri, "trustedBiddingUri");
            return this;
        }

        public Builder setTrustedBiddingKeys(List<String> trustedBiddingKeys) {
            Objects.requireNonNull(trustedBiddingKeys, "trustedBiddingKeys");
            this.mTrustedBiddingKeys = new ArrayList<String>(trustedBiddingKeys);
            return this;
        }

        public TrustedBiddingData build() {
            return new TrustedBiddingData(mTrustedBiddingUri, mTrustedBiddingKeys);
        }
    }

    public static final Parcelable.Creator<TrustedBiddingData> CREATOR =
            new Parcelable.Creator<TrustedBiddingData>() {
                @Override
                public TrustedBiddingData createFromParcel(Parcel in) {
                    Objects.requireNonNull(in, "in");
                    Uri uri = Uri.parse(in.readString());
                    int count = in.readInt();
                    List<String> keys = new ArrayList<String>(count);
                    for (int i = 0; i < count; i++) {
                        keys.add(in.readString());
                    }
                    return new TrustedBiddingData(uri, keys);
                }

                @Override
                public TrustedBiddingData[] newArray(int size) {
                    return new TrustedBiddingData[size];
                }
            };
}
