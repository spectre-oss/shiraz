// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.common;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

/** Represents an ad tech domain identifier (e.g. buyer or seller eTLD+1). */
public final class AdTechIdentifier implements Parcelable {
    private final String mIdentifier;

    private AdTechIdentifier(String identifier) {
        this.mIdentifier = Objects.requireNonNull(identifier, "identifier");
    }

    public static AdTechIdentifier fromString(String source) {
        Objects.requireNonNull(source, "source");
        return new AdTechIdentifier(source);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Objects.requireNonNull(dest, "dest");
        dest.writeString(mIdentifier);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AdTechIdentifier)) {
            return false;
        }
        AdTechIdentifier that = (AdTechIdentifier) o;
        return mIdentifier.equals(that.mIdentifier);
    }

    @Override
    public int hashCode() {
        return mIdentifier.hashCode();
    }

    @Override
    public String toString() {
        return mIdentifier;
    }

    public static final Parcelable.Creator<AdTechIdentifier> CREATOR =
            new Parcelable.Creator<AdTechIdentifier>() {
                @Override
                public AdTechIdentifier createFromParcel(Parcel in) {
                    Objects.requireNonNull(in, "in");
                    return new AdTechIdentifier(in.readString());
                }

                @Override
                public AdTechIdentifier[] newArray(int size) {
                    return new AdTechIdentifier[size];
                }
            };
}
