// SPDX-License-Identifier: GPL-3.0-only
package android.net;

import android.os.Parcel;
import android.os.Parcelable;

/** Network specifier selecting a cellular subscription by subscription ID. */
public final class TelephonyNetworkSpecifier extends NetworkSpecifier implements Parcelable {
    private final int mSubId;

    TelephonyNetworkSpecifier(int subId) {
        this.mSubId = subId;
    }

    public int getSubscriptionId() {
        return mSubId;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mSubId);
    }

    @Override
    public int hashCode() {
        return mSubId;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof TelephonyNetworkSpecifier)) {
            return false;
        }
        return mSubId == ((TelephonyNetworkSpecifier) obj).mSubId;
    }

    @Override
    public String toString() {
        return "TelephonyNetworkSpecifier [mSubId = " + mSubId + "]";
    }

    public static final class Builder {
        private static final int SENTINEL_SUB_ID = Integer.MIN_VALUE;
        private int mSubId = SENTINEL_SUB_ID;

        public Builder() {
        }

        public Builder setSubscriptionId(int subId) {
            this.mSubId = subId;
            return this;
        }

        public TelephonyNetworkSpecifier build() {
            if (mSubId == SENTINEL_SUB_ID) {
                throw new IllegalArgumentException("SubscriptionId is not provided.");
            }
            return new TelephonyNetworkSpecifier(mSubId);
        }
    }

    public static final Parcelable.Creator<TelephonyNetworkSpecifier> CREATOR =
            new Parcelable.Creator<TelephonyNetworkSpecifier>() {
                @Override
                public TelephonyNetworkSpecifier createFromParcel(Parcel in) {
                    return new TelephonyNetworkSpecifier(in.readInt());
                }

                @Override
                public TelephonyNetworkSpecifier[] newArray(int size) {
                    return new TelephonyNetworkSpecifier[size];
                }
            };
}
