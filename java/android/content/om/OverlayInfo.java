// SPDX-License-Identifier: GPL-3.0-only
package android.content.om;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

/** Immutable metadata describing a registered Runtime Resource Overlay. */
public final class OverlayInfo implements Parcelable {
    private final String mPackageName;
    private final String mOverlayName;
    private final String mTargetPackageName;
    private final String mTargetOverlayableName;

    public OverlayInfo(
            String packageName,
            String overlayName,
            String targetPackageName,
            String targetOverlayableName) {
        mPackageName = Objects.requireNonNull(packageName);
        mOverlayName = overlayName;
        mTargetPackageName = Objects.requireNonNull(targetPackageName);
        mTargetOverlayableName = targetOverlayableName;
    }

    OverlayInfo(Parcel in) {
        mPackageName = in.readString();
        mOverlayName = in.readString();
        mTargetPackageName = in.readString();
        mTargetOverlayableName = in.readString();
    }

    public String getOverlayName() {
        return mOverlayName;
    }

    public String getTargetPackageName() {
        return mTargetPackageName;
    }

    public String getTargetOverlayableName() {
        return mTargetOverlayableName;
    }

    public OverlayIdentifier getOverlayIdentifier() {
        return new OverlayIdentifier(mPackageName, mOverlayName);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mPackageName);
        dest.writeString(mOverlayName);
        dest.writeString(mTargetPackageName);
        dest.writeString(mTargetOverlayableName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mPackageName, mOverlayName, mTargetPackageName, mTargetOverlayableName);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof OverlayInfo)) return false;
        OverlayInfo other = (OverlayInfo) obj;
        return Objects.equals(mPackageName, other.mPackageName)
                && Objects.equals(mOverlayName, other.mOverlayName)
                && Objects.equals(mTargetPackageName, other.mTargetPackageName)
                && Objects.equals(mTargetOverlayableName, other.mTargetOverlayableName);
    }

    @Override
    public String toString() {
        return "OverlayInfo { overlay=" + getOverlayIdentifier()
                + ", targetPackage=" + mTargetPackageName
                + ", targetOverlayable=" + mTargetOverlayableName + " }";
    }

    public static final Creator<OverlayInfo> CREATOR = new Creator<OverlayInfo>() {
        @Override
        public OverlayInfo createFromParcel(Parcel source) {
            return new OverlayInfo(source);
        }

        @Override
        public OverlayInfo[] newArray(int size) {
            return new OverlayInfo[size];
        }
    };
}
