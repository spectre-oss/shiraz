// SPDX-License-Identifier: GPL-3.0-only
package android.content.om;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

/** Unique key identifying a runtime resource overlay by package name and optional overlay name. */
public final class OverlayIdentifier implements Parcelable {
    private final String mPackageName;
    private final String mOverlayName;

    public OverlayIdentifier(String packageName, String overlayName) {
        mPackageName = packageName;
        mOverlayName = overlayName;
    }

    public OverlayIdentifier(String packageName) {
        this(packageName, null);
    }

    OverlayIdentifier(Parcel in) {
        mPackageName = in.readString();
        mOverlayName = in.readString();
    }

    public String getPackageName() {
        return mPackageName;
    }

    public String getOverlayName() {
        return mOverlayName;
    }

    @Override
    public String toString() {
        return mOverlayName == null ? String.valueOf(mPackageName) : (mPackageName + ":" + mOverlayName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OverlayIdentifier)) return false;
        OverlayIdentifier that = (OverlayIdentifier) o;
        return Objects.equals(mPackageName, that.mPackageName)
                && Objects.equals(mOverlayName, that.mOverlayName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mPackageName, mOverlayName);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mPackageName);
        dest.writeString(mOverlayName);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<OverlayIdentifier> CREATOR = new Creator<OverlayIdentifier>() {
        @Override
        public OverlayIdentifier createFromParcel(Parcel source) {
            return new OverlayIdentifier(source);
        }

        @Override
        public OverlayIdentifier[] newArray(int size) {
            return new OverlayIdentifier[size];
        }
    };
}
