// SPDX-License-Identifier: GPL-3.0-only
package android.media.tv;

import android.os.Parcel;
import android.os.Parcelable;

public final class AitInfo implements Parcelable {
    private final int mType;
    private final int mVersion;

    public AitInfo(int type, int version) {
        mType = type;
        mVersion = version;
    }

    public int getType() {
        return mType;
    }

    public int getVersion() {
        return mVersion;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mType);
        dest.writeInt(mVersion);
    }

    @Override
    public String toString() {
        return "type=" + mType + ";version=" + mVersion;
    }

    public static final Parcelable.Creator<AitInfo> CREATOR =
            new Parcelable.Creator<AitInfo>() {
                @Override
                public AitInfo createFromParcel(Parcel in) {
                    return new AitInfo(in.readInt(), in.readInt());
                }

                @Override
                public AitInfo[] newArray(int size) {
                    return new AitInfo[size];
                }
            };
}
