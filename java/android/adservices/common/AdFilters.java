// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.common;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

/** Container for frequency cap and app-install ad filters. */
public final class AdFilters implements Parcelable {
    private final FrequencyCapFilters mFrequencyCapFilters;
    private final AppInstallFilters mAppInstallFilters;

    private AdFilters(
            FrequencyCapFilters frequencyCapFilters, AppInstallFilters appInstallFilters) {
        this.mFrequencyCapFilters = frequencyCapFilters;
        this.mAppInstallFilters = appInstallFilters;
    }

    public FrequencyCapFilters getFrequencyCapFilters() {
        return mFrequencyCapFilters;
    }

    public AppInstallFilters getAppInstallFilters() {
        return mAppInstallFilters;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Objects.requireNonNull(dest, "dest");
        dest.writeTypedObject(mFrequencyCapFilters, flags);
        dest.writeTypedObject(mAppInstallFilters, flags);
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
        if (!(o instanceof AdFilters)) {
            return false;
        }
        AdFilters that = (AdFilters) o;
        return Objects.equals(mFrequencyCapFilters, that.mFrequencyCapFilters)
                && Objects.equals(mAppInstallFilters, that.mAppInstallFilters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mFrequencyCapFilters, mAppInstallFilters);
    }

    @Override
    public String toString() {
        return "AdFilters{mFrequencyCapFilters="
                + mFrequencyCapFilters
                + ", mAppInstallFilters="
                + mAppInstallFilters
                + "}";
    }

    public static final class Builder {
        private FrequencyCapFilters mFrequencyCapFilters;
        private AppInstallFilters mAppInstallFilters;

        public Builder() {
        }

        public Builder setFrequencyCapFilters(FrequencyCapFilters frequencyCapFilters) {
            this.mFrequencyCapFilters = frequencyCapFilters;
            return this;
        }

        public Builder setAppInstallFilters(AppInstallFilters appInstallFilters) {
            this.mAppInstallFilters = appInstallFilters;
            return this;
        }

        public AdFilters build() {
            return new AdFilters(mFrequencyCapFilters, mAppInstallFilters);
        }
    }

    public static final Parcelable.Creator<AdFilters> CREATOR =
            new Parcelable.Creator<AdFilters>() {
                @Override
                public AdFilters createFromParcel(Parcel in) {
                    Objects.requireNonNull(in, "in");
                    FrequencyCapFilters freq = in.readTypedObject(FrequencyCapFilters.CREATOR);
                    AppInstallFilters app = in.readTypedObject(AppInstallFilters.CREATOR);
                    return new AdFilters(freq, app);
                }

                @Override
                public AdFilters[] newArray(int size) {
                    return new AdFilters[size];
                }
            };
}
