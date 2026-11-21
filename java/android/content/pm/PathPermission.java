// SPDX-License-Identifier: GPL-3.0-only
package android.content.pm;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.PatternMatcher;

/** PatternMatcher for ContentProvider URI paths paired with read and write permission names. */
public class PathPermission extends PatternMatcher {
    private final String mReadPermission;
    private final String mWritePermission;

    public PathPermission(String pattern, int type, String readPermission, String writePermission) {
        super(pattern, type);
        mReadPermission = readPermission;
        mWritePermission = writePermission;
    }

    public PathPermission(Parcel src) {
        super(src);
        mReadPermission = src.readString();
        mWritePermission = src.readString();
    }

    public String getReadPermission() {
        return mReadPermission;
    }

    public String getWritePermission() {
        return mWritePermission;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeString(mReadPermission);
        dest.writeString(mWritePermission);
    }

    public static final Parcelable.Creator<PathPermission> CREATOR =
            new Parcelable.Creator<PathPermission>() {
                @Override
                public PathPermission createFromParcel(Parcel source) {
                    return new PathPermission(source);
                }

                @Override
                public PathPermission[] newArray(int size) {
                    return new PathPermission[size];
                }
            };
}
