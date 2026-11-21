// SPDX-License-Identifier: GPL-3.0-only
package android.app.admin;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class PackagePolicy implements Parcelable {
    public static final int PACKAGE_POLICY_BLOCKLIST = 1;
    public static final int PACKAGE_POLICY_ALLOWLIST_AND_SYSTEM = 2;
    public static final int PACKAGE_POLICY_ALLOWLIST = 3;

    private final int mPolicyType;
    private final Set<String> mPackageNames;

    public PackagePolicy(int policyType) {
        this(policyType, Collections.<String>emptySet());
    }

    public PackagePolicy(int policyType, Set<String> packageNames) {
        if (policyType < PACKAGE_POLICY_BLOCKLIST || policyType > PACKAGE_POLICY_ALLOWLIST) {
            throw new IllegalArgumentException("Invalid policy type: " + policyType);
        }
        if (packageNames == null) {
            throw new NullPointerException("packageNames");
        }
        mPolicyType = policyType;
        mPackageNames = Collections.unmodifiableSet(new HashSet<String>(packageNames));
    }

    PackagePolicy(Parcel in) {
        mPolicyType = in.readInt();
        int count = in.readInt();
        Set<String> set = new HashSet<String>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            set.add(in.readString());
        }
        mPackageNames = Collections.unmodifiableSet(set);
    }

    public int getPolicyType() {
        return mPolicyType;
    }

    public Set<String> getPackageNames() {
        return mPackageNames;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mPolicyType);
        dest.writeInt(mPackageNames.size());
        for (String pkg : mPackageNames) {
            dest.writeString(pkg);
        }
    }

    @Override
    public boolean equals(Object thatObject) {
        if (this == thatObject) return true;
        if (!(thatObject instanceof PackagePolicy)) return false;
        PackagePolicy that = (PackagePolicy) thatObject;
        return mPolicyType == that.mPolicyType
                && Objects.equals(mPackageNames, that.mPackageNames);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Integer.valueOf(mPolicyType), mPackageNames);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<PackagePolicy> CREATOR =
            new Parcelable.Creator<PackagePolicy>() {
                @Override
                public PackagePolicy createFromParcel(Parcel in) {
                    return new PackagePolicy(in);
                }

                @Override
                public PackagePolicy[] newArray(int size) {
                    return new PackagePolicy[size];
                }
            };
}
