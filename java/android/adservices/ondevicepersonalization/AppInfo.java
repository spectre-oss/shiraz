// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.ondevicepersonalization;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

/** Information about an application exposed through {@link UserData}. */
public final class AppInfo implements Parcelable {
    private final boolean mInstalled;

    AppInfo(boolean installed) {
        this.mInstalled = installed;
    }

    public boolean isInstalled() {
        return mInstalled;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AppInfo)) {
            return false;
        }
        AppInfo that = (AppInfo) o;
        return mInstalled == that.mInstalled;
    }

    @Override
    public int hashCode() {
        return Objects.hash(Boolean.valueOf(mInstalled));
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Objects.requireNonNull(dest, "dest");
        dest.writeBoolean(mInstalled);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<AppInfo> CREATOR =
            new Parcelable.Creator<AppInfo>() {
                @Override
                public AppInfo createFromParcel(Parcel in) {
                    Objects.requireNonNull(in, "in");
                    return new AppInfo(in.readBoolean());
                }

                @Override
                public AppInfo[] newArray(int size) {
                    return new AppInfo[size];
                }
            };
}
