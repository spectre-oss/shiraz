// SPDX-License-Identifier: GPL-3.0-only
package android.app.appsearch;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class SchemaVisibilityConfig implements Parcelable {
    private final List<PackageIdentifier> mAllowedPackages;
    private final Set<Set<Integer>> mRequiredPermissions;
    private final PackageIdentifier mPubliclyVisibleTargetPackage;

    private SchemaVisibilityConfig(
            List<PackageIdentifier> allowedPackages,
            Set<Set<Integer>> requiredPermissions,
            PackageIdentifier publiclyVisibleTargetPackage) {
        mAllowedPackages = Collections.unmodifiableList(
                new ArrayList<PackageIdentifier>(allowedPackages));
        Set<Set<Integer>> permCopy = new LinkedHashSet<Set<Integer>>();
        for (Set<Integer> inner : requiredPermissions) {
            permCopy.add(Collections.unmodifiableSet(new LinkedHashSet<Integer>(inner)));
        }
        mRequiredPermissions = Collections.unmodifiableSet(permCopy);
        mPubliclyVisibleTargetPackage = publiclyVisibleTargetPackage;
    }

    SchemaVisibilityConfig(Parcel in) {
        int pkgCount = in.readInt();
        List<PackageIdentifier> pkgs = new ArrayList<PackageIdentifier>(Math.max(0, pkgCount));
        for (int i = 0; i < pkgCount; i++) {
            pkgs.add(new PackageIdentifier(in.readString(), in.createByteArray()));
        }
        mAllowedPackages = Collections.unmodifiableList(pkgs);
        int permSetCount = in.readInt();
        Set<Set<Integer>> permSets = new LinkedHashSet<Set<Integer>>();
        for (int i = 0; i < permSetCount; i++) {
            int innerCount = in.readInt();
            Set<Integer> inner = new LinkedHashSet<Integer>();
            for (int j = 0; j < innerCount; j++) {
                inner.add(Integer.valueOf(in.readInt()));
            }
            permSets.add(Collections.unmodifiableSet(inner));
        }
        mRequiredPermissions = Collections.unmodifiableSet(permSets);
        if (in.readInt() != 0) {
            mPubliclyVisibleTargetPackage =
                    new PackageIdentifier(in.readString(), in.createByteArray());
        } else {
            mPubliclyVisibleTargetPackage = null;
        }
    }

    public List<PackageIdentifier> getAllowedPackages() {
        return mAllowedPackages;
    }

    public Set<Set<Integer>> getRequiredPermissions() {
        return mRequiredPermissions;
    }

    public PackageIdentifier getPubliclyVisibleTargetPackage() {
        return mPubliclyVisibleTargetPackage;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mAllowedPackages.size());
        for (PackageIdentifier p : mAllowedPackages) {
            dest.writeString(p.getPackageName());
            dest.writeByteArray(p.getSha256Certificate());
        }
        dest.writeInt(mRequiredPermissions.size());
        for (Set<Integer> inner : mRequiredPermissions) {
            dest.writeInt(inner.size());
            for (Integer perm : inner) {
                dest.writeInt(perm.intValue());
            }
        }
        if (mPubliclyVisibleTargetPackage != null) {
            dest.writeInt(1);
            dest.writeString(mPubliclyVisibleTargetPackage.getPackageName());
            dest.writeByteArray(mPubliclyVisibleTargetPackage.getSha256Certificate());
        } else {
            dest.writeInt(0);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SchemaVisibilityConfig)) return false;
        SchemaVisibilityConfig that = (SchemaVisibilityConfig) o;
        return Objects.equals(mAllowedPackages, that.mAllowedPackages)
                && Objects.equals(mRequiredPermissions, that.mRequiredPermissions)
                && Objects.equals(
                        mPubliclyVisibleTargetPackage, that.mPubliclyVisibleTargetPackage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mAllowedPackages, mRequiredPermissions, mPubliclyVisibleTargetPackage);
    }

    @Override
    public final int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<SchemaVisibilityConfig> CREATOR =
            new Parcelable.Creator<SchemaVisibilityConfig>() {
                @Override
                public SchemaVisibilityConfig createFromParcel(Parcel in) {
                    return new SchemaVisibilityConfig(in);
                }

                @Override
                public SchemaVisibilityConfig[] newArray(int size) {
                    return new SchemaVisibilityConfig[size];
                }
            };

    public static final class Builder {
        private final List<PackageIdentifier> mAllowedPackages =
                new ArrayList<PackageIdentifier>();
        private final Set<Set<Integer>> mRequiredPermissions = new LinkedHashSet<Set<Integer>>();
        private PackageIdentifier mPubliclyVisibleTargetPackage;

        public Builder() {
        }

        public Builder addAllowedPackage(PackageIdentifier packageIdentifier) {
            Objects.requireNonNull(packageIdentifier);
            if (!mAllowedPackages.contains(packageIdentifier)) {
                mAllowedPackages.add(packageIdentifier);
            }
            return this;
        }

        public Builder clearAllowedPackages() {
            mAllowedPackages.clear();
            return this;
        }

        public Builder addRequiredPermissions(Set<Integer> visibleToPermissions) {
            Objects.requireNonNull(visibleToPermissions);
            mRequiredPermissions.add(new LinkedHashSet<Integer>(visibleToPermissions));
            return this;
        }

        public Builder clearRequiredPermissions() {
            mRequiredPermissions.clear();
            return this;
        }

        public Builder setPubliclyVisibleTargetPackage(PackageIdentifier packageIdentifier) {
            mPubliclyVisibleTargetPackage = packageIdentifier;
            return this;
        }

        public SchemaVisibilityConfig build() {
            return new SchemaVisibilityConfig(
                    mAllowedPackages, mRequiredPermissions, mPubliclyVisibleTargetPackage);
        }
    }
}
