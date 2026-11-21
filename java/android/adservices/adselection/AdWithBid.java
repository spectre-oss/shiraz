// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.adselection;

import android.adservices.common.AdData;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

/** Pairs an {@link AdData} creative with its bid value for contextual ad selection. */
public final class AdWithBid implements Parcelable {
    private final AdData mAdData;
    private final double mBid;

    public AdWithBid(AdData adData, double bid) {
        this.mAdData = Objects.requireNonNull(adData, "adData");
        this.mBid = bid;
    }

    public AdData getAdData() {
        return mAdData;
    }

    public double getBid() {
        return mBid;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Objects.requireNonNull(dest, "dest");
        dest.writeTypedObject(mAdData, flags);
        dest.writeDouble(mBid);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AdWithBid)) {
            return false;
        }
        AdWithBid that = (AdWithBid) o;
        return Double.compare(that.mBid, mBid) == 0 && Objects.equals(mAdData, that.mAdData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mAdData, Double.valueOf(mBid));
    }

    public static final Parcelable.Creator<AdWithBid> CREATOR =
            new Parcelable.Creator<AdWithBid>() {
                @Override
                public AdWithBid createFromParcel(Parcel in) {
                    Objects.requireNonNull(in, "in");
                    AdData adData = in.readTypedObject(AdData.CREATOR);
                    double bid = in.readDouble();
                    return new AdWithBid(adData, bid);
                }

                @Override
                public AdWithBid[] newArray(int size) {
                    return new AdWithBid[size];
                }
            };
}
