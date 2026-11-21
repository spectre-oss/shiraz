// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.common;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Ad filter criteria based on installed application package names. */
public final class AppInstallFilters implements Parcelable {
    private final Set<String> mPackageNames;

    private AppInstallFilters(Set<String> packageNames) {
        Set<String> copy = new LinkedHashSet<String>();
        if (packageNames != null) {
            for (String pkg : packageNames) {
                Objects.requireNonNull(pkg, "packageName");
                copy.add(pkg);
            }
        }
        this.mPackageNames = Collections.unmodifiableSet(copy);
    }

    public Set<String> getPackageNames() {
        return mPackageNames;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Objects.requireNonNull(dest, "dest");
        dest.writeInt(mPackageNames.size());
        for (String pkg : mPackageNames) {
            dest.writeString(pkg);
        }
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
        if (!(o instanceof AppInstallFilters)) {
            return false;
        }
        AppInstallFilters that = (AppInstallFilters) o;
        return Objects.equals(mPackageNames, that.mPackageNames);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mPackageNames);
    }

    @Override
    public String toString() {
        return "AppInstallFilters{mPackageNames=" + mPackageNames + "}";
    }

    public static final class Builder {
        private Set<String> mPackageNames = Collections.emptySet();

        public Builder() {
        }

        public Builder setPackageNames(Set<String> packageNames) {
            Objects.requireNonNull(packageNames, "packageNames");
            this.mPackageNames = new LinkedHashSet<String>(packageNames);
            return this;
        }

        public AppInstallFilters build() {
            return new AppInstallFilters(mPackageNames);
        }
    }

    public static final Parcelable.Creator<AppInstallFilters> CREATOR =
            new Parcelable.Creator<AppInstallFilters>() {
                @Override
                public AppInstallFilters createFromParcel(Parcel in) {
                    Objects.requireNonNull(in, "in");
                    int count = in.readInt();
                    Set<String> names = new LinkedHashSet<String>();
                    for (int i = 0; i < count; i++) {
                        names.add(in.readString());
                    }
                    return new AppInstallFilters(names);
                }

                @Override
                public AppInstallFilters[] newArray(int size) {
                    return new AppInstallFilters[size];
                }
            };
}
