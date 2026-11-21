// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.adselection;

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Configuration for selecting a winning ad from prior {@link AdSelectionOutcome}s. */
public final class AdSelectionFromOutcomesConfig implements Parcelable {
    private final AdTechIdentifier mSeller;
    private final List<Long> mAdSelectionIds;
    private final AdSelectionSignals mSelectionSignals;
    private final Uri mSelectionLogicUri;

    private AdSelectionFromOutcomesConfig(
            AdTechIdentifier seller,
            List<Long> adSelectionIds,
            AdSelectionSignals selectionSignals,
            Uri selectionLogicUri) {
        this.mSeller = Objects.requireNonNull(seller, "seller");
        Objects.requireNonNull(adSelectionIds, "adSelectionIds");
        List<Long> copy = new ArrayList<Long>(adSelectionIds.size());
        for (Long id : adSelectionIds) {
            copy.add(Objects.requireNonNull(id, "adSelectionId"));
        }
        this.mAdSelectionIds = Collections.unmodifiableList(copy);
        this.mSelectionSignals =
                Objects.requireNonNull(selectionSignals, "selectionSignals");
        this.mSelectionLogicUri =
                Objects.requireNonNull(selectionLogicUri, "selectionLogicUri");
    }

    public AdTechIdentifier getSeller() {
        return mSeller;
    }

    public List<Long> getAdSelectionIds() {
        return mAdSelectionIds;
    }

    public AdSelectionSignals getSelectionSignals() {
        return mSelectionSignals;
    }

    public Uri getSelectionLogicUri() {
        return mSelectionLogicUri;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Objects.requireNonNull(dest, "dest");
        dest.writeTypedObject(mSeller, flags);
        dest.writeInt(mAdSelectionIds.size());
        for (Long id : mAdSelectionIds) {
            dest.writeLong(id.longValue());
        }
        dest.writeTypedObject(mSelectionSignals, flags);
        dest.writeString(mSelectionLogicUri.toString());
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
        if (!(o instanceof AdSelectionFromOutcomesConfig)) {
            return false;
        }
        AdSelectionFromOutcomesConfig that = (AdSelectionFromOutcomesConfig) o;
        return Objects.equals(mSeller, that.mSeller)
                && Objects.equals(mAdSelectionIds, that.mAdSelectionIds)
                && Objects.equals(mSelectionSignals, that.mSelectionSignals)
                && Objects.equals(mSelectionLogicUri, that.mSelectionLogicUri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mSeller, mAdSelectionIds, mSelectionSignals, mSelectionLogicUri);
    }

    public static final class Builder {
        private AdTechIdentifier mSeller;
        private List<Long> mAdSelectionIds;
        private AdSelectionSignals mSelectionSignals;
        private Uri mSelectionLogicUri;

        public Builder() {
        }

        public Builder setSeller(AdTechIdentifier seller) {
            this.mSeller = Objects.requireNonNull(seller, "seller");
            return this;
        }

        public Builder setAdSelectionIds(List<Long> adSelectionIds) {
            Objects.requireNonNull(adSelectionIds, "adSelectionIds");
            this.mAdSelectionIds = new ArrayList<Long>(adSelectionIds);
            return this;
        }

        public Builder setSelectionSignals(AdSelectionSignals selectionSignals) {
            this.mSelectionSignals =
                    Objects.requireNonNull(selectionSignals, "selectionSignals");
            return this;
        }

        public Builder setSelectionLogicUri(Uri selectionLogicUri) {
            this.mSelectionLogicUri =
                    Objects.requireNonNull(selectionLogicUri, "selectionLogicUri");
            return this;
        }

        public AdSelectionFromOutcomesConfig build() {
            return new AdSelectionFromOutcomesConfig(
                    mSeller, mAdSelectionIds, mSelectionSignals, mSelectionLogicUri);
        }
    }

    public static final Parcelable.Creator<AdSelectionFromOutcomesConfig> CREATOR =
            new Parcelable.Creator<AdSelectionFromOutcomesConfig>() {
                @Override
                public AdSelectionFromOutcomesConfig createFromParcel(Parcel in) {
                    Objects.requireNonNull(in, "in");
                    AdTechIdentifier seller = in.readTypedObject(AdTechIdentifier.CREATOR);
                    int count = in.readInt();
                    List<Long> ids = new ArrayList<Long>(count);
                    for (int i = 0; i < count; i++) {
                        ids.add(Long.valueOf(in.readLong()));
                    }
                    AdSelectionSignals signals =
                            in.readTypedObject(AdSelectionSignals.CREATOR);
                    Uri uri = Uri.parse(in.readString());
                    return new AdSelectionFromOutcomesConfig(seller, ids, signals, uri);
                }

                @Override
                public AdSelectionFromOutcomesConfig[] newArray(int size) {
                    return new AdSelectionFromOutcomesConfig[size];
                }
            };
}
