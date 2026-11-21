// SPDX-License-Identifier: GPL-3.0-only
package android.content.pm;

import android.os.Parcel;
import android.os.Parcelable;

/** Metadata describing a user profile exposed to LauncherApps. */
public final class LauncherUserInfo implements Parcelable {
    public static final String PRIVATE_SPACE_ENTRYPOINT_HIDDEN =
            "private_space_entrypoint_hidden";

    private final String mUserType;
    private final int mUserSerialNumber;

    public LauncherUserInfo(String userType, int userSerialNumber) {
        mUserType = userType != null ? userType : "android.os.usertype.full.SYSTEM";
        mUserSerialNumber = userSerialNumber;
    }

    LauncherUserInfo(Parcel in) {
        mUserType = in.readString();
        mUserSerialNumber = in.readInt();
    }

    public String getUserType() {
        return mUserType;
    }

    public int getUserSerialNumber() {
        return mUserSerialNumber;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mUserType);
        dest.writeInt(mUserSerialNumber);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<LauncherUserInfo> CREATOR = new Creator<LauncherUserInfo>() {
        @Override
        public LauncherUserInfo createFromParcel(Parcel in) {
            return new LauncherUserInfo(in);
        }

        @Override
        public LauncherUserInfo[] newArray(int size) {
            return new LauncherUserInfo[size];
        }
    };
}
