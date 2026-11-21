// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.common;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Represents creative render URI, metadata, counter keys, and filters for an ad. */
public final class AdData implements Parcelable {
    private final Uri mRenderUri;
    private final String mMetadata;
    private final Set<Integer> mAdCounterKeys;
    private final AdFilters mAdFilters;
    private final String mAdRenderId;

    private AdData(
            Uri renderUri,
            String metadata,
            Set<Integer> adCounterKeys,
            AdFilters adFilters,
            String adRenderId) {
        this.mRenderUri = Objects.requireNonNull(renderUri, "renderUri");
        this.mMetadata = Objects.requireNonNull(metadata, "metadata");
        Set<Integer> keys = new LinkedHashSet<Integer>();
        if (adCounterKeys != null) {
            for (Integer k : adCounterKeys) {
                keys.add(Objects.requireNonNull(k, "adCounterKey"));
            }
        }
        this.mAdCounterKeys = Collections.unmodifiableSet(keys);
        this.mAdFilters = adFilters;
        this.mAdRenderId = adRenderId;
    }

    public Uri getRenderUri() {
        return mRenderUri;
    }

    public String getMetadata() {
        return mMetadata;
    }

    public Set<Integer> getAdCounterKeys() {
        return mAdCounterKeys;
    }

    public AdFilters getAdFilters() {
        return mAdFilters;
    }

    public String getAdRenderId() {
        return mAdRenderId;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Objects.requireNonNull(dest, "dest");
        dest.writeString(mRenderUri.toString());
        dest.writeString(mMetadata);
        dest.writeInt(mAdCounterKeys.size());
        for (Integer key : mAdCounterKeys) {
            dest.writeInt(key.intValue());
        }
        dest.writeTypedObject(mAdFilters, flags);
        dest.writeString(mAdRenderId);
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
        if (!(o instanceof AdData)) {
            return false;
        }
        AdData that = (AdData) o;
        return Objects.equals(mRenderUri, that.mRenderUri)
                && Objects.equals(mMetadata, that.mMetadata)
                && Objects.equals(mAdCounterKeys, that.mAdCounterKeys)
                && Objects.equals(mAdFilters, that.mAdFilters)
                && Objects.equals(mAdRenderId, that.mAdRenderId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mRenderUri, mMetadata, mAdCounterKeys, mAdFilters, mAdRenderId);
    }

    @Override
    public String toString() {
        return "AdData{mRenderUri="
                + mRenderUri
                + ", mMetadata='"
                + mMetadata
                + "', mAdCounterKeys="
                + mAdCounterKeys
                + ", mAdFilters="
                + mAdFilters
                + ", mAdRenderId='"
                + mAdRenderId
                + "'}";
    }

    public static final class Builder {
        private Uri mRenderUri;
        private String mMetadata;
        private Set<Integer> mAdCounterKeys = Collections.emptySet();
        private AdFilters mAdFilters;
        private String mAdRenderId;

        public Builder() {
        }

        public Builder setRenderUri(Uri renderUri) {
            this.mRenderUri = Objects.requireNonNull(renderUri, "renderUri");
            return this;
        }

        public Builder setMetadata(String metadata) {
            this.mMetadata = Objects.requireNonNull(metadata, "metadata");
            return this;
        }

        public Builder setAdCounterKeys(Set<Integer> adCounterKeys) {
            Objects.requireNonNull(adCounterKeys, "adCounterKeys");
            this.mAdCounterKeys = new LinkedHashSet<Integer>(adCounterKeys);
            return this;
        }

        public Builder setAdFilters(AdFilters adFilters) {
            this.mAdFilters = adFilters;
            return this;
        }

        public Builder setAdRenderId(String adRenderId) {
            this.mAdRenderId = adRenderId;
            return this;
        }

        public AdData build() {
            return new AdData(mRenderUri, mMetadata, mAdCounterKeys, mAdFilters, mAdRenderId);
        }
    }

    public static final Parcelable.Creator<AdData> CREATOR =
            new Parcelable.Creator<AdData>() {
                @Override
                public AdData createFromParcel(Parcel in) {
                    Objects.requireNonNull(in, "in");
                    Uri renderUri = Uri.parse(in.readString());
                    String metadata = in.readString();
                    int count = in.readInt();
                    Set<Integer> keys = new LinkedHashSet<Integer>();
                    for (int i = 0; i < count; i++) {
                        keys.add(Integer.valueOf(in.readInt()));
                    }
                    AdFilters filters = in.readTypedObject(AdFilters.CREATOR);
                    String renderId = in.readString();
                    return new AdData(renderUri, metadata, keys, filters, renderId);
                }

                @Override
                public AdData[] newArray(int size) {
                    return new AdData[size];
                }
            };
}
