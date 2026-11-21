// SPDX-License-Identifier: GPL-3.0-only
package android.app.admin;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class ManagedSubscriptionsPolicy implements Parcelable {
    public static final int TYPE_ALL_PERSONAL_SUBSCRIPTIONS = 0;
    public static final int TYPE_ALL_MANAGED_SUBSCRIPTIONS = 1;

    private final int mPolicyType;

    public ManagedSubscriptionsPolicy(int policyType) {
        if (policyType != TYPE_ALL_PERSONAL_SUBSCRIPTIONS
                && policyType != TYPE_ALL_MANAGED_SUBSCRIPTIONS) {
            throw new IllegalArgumentException("Invalid policy type: " + policyType);
        }
        mPolicyType = policyType;
    }

    public int getPolicyType() {
        return mPolicyType;
    }

    @Override
    public String toString() {
        return "ManagedSubscriptionsPolicy(type: " + mPolicyType + ")";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mPolicyType);
    }

    @Override
    public boolean equals(Object thatObject) {
        if (this == thatObject) return true;
        if (!(thatObject instanceof ManagedSubscriptionsPolicy)) return false;
        ManagedSubscriptionsPolicy that = (ManagedSubscriptionsPolicy) thatObject;
        return mPolicyType == that.mPolicyType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(Integer.valueOf(mPolicyType));
    }

    public static final Parcelable.Creator<ManagedSubscriptionsPolicy> CREATOR =
            new Parcelable.Creator<ManagedSubscriptionsPolicy>() {
                @Override
                public ManagedSubscriptionsPolicy createFromParcel(Parcel in) {
                    return new ManagedSubscriptionsPolicy(in.readInt());
                }

                @Override
                public ManagedSubscriptionsPolicy[] newArray(int size) {
                    return new ManagedSubscriptionsPolicy[size];
                }
            };
}
