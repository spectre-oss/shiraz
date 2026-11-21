// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.common;

import android.os.Parcel;
import android.os.Parcelable;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** JSON string wrapper carrying buyer, seller, or user signals for ad selection. */
public final class AdSelectionSignals implements Parcelable {
    public static final AdSelectionSignals EMPTY = new AdSelectionSignals("{}");

    private final String mSignals;

    private AdSelectionSignals(String signals) {
        this.mSignals = Objects.requireNonNull(signals, "signals");
    }

    public static AdSelectionSignals fromString(String source) {
        Objects.requireNonNull(source, "source");
        return new AdSelectionSignals(source);
    }

    int getSizeInBytes() {
        return mSignals.getBytes(StandardCharsets.UTF_8).length;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Objects.requireNonNull(dest, "dest");
        dest.writeString(mSignals);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AdSelectionSignals)) {
            return false;
        }
        AdSelectionSignals that = (AdSelectionSignals) o;
        return mSignals.equals(that.mSignals);
    }

    @Override
    public int hashCode() {
        return mSignals.hashCode();
    }

    @Override
    public String toString() {
        return mSignals;
    }

    public static final Parcelable.Creator<AdSelectionSignals> CREATOR =
            new Parcelable.Creator<AdSelectionSignals>() {
                @Override
                public AdSelectionSignals createFromParcel(Parcel in) {
                    Objects.requireNonNull(in, "in");
                    return new AdSelectionSignals(in.readString());
                }

                @Override
                public AdSelectionSignals[] newArray(int size) {
                    return new AdSelectionSignals[size];
                }
            };
}
