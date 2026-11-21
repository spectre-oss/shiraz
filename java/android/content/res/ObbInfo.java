// SPDX-License-Identifier: GPL-3.0-only
package android.content.res;

import android.os.Parcel;
import android.os.Parcelable;

/** Metadata extracted from an Android Opaque Binary Blob (OBB) container file. */
public class ObbInfo implements Parcelable {
    public static final int OBB_OVERLAY = 1;

    public String filename;
    public String packageName;
    public int version;
    public int flags;
    public byte[] salt;

    public ObbInfo() {}

    ObbInfo(Parcel source) {
        filename = source.readString();
        packageName = source.readString();
        version = source.readInt();
        flags = source.readInt();
        salt = source.createByteArray();
    }

    @Override
    public String toString() {
        return "ObbInfo{" + Integer.toHexString(System.identityHashCode(this))
                + " packageName=" + packageName
                + ",version=" + version
                + ",flags=" + flags + '}';
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int parcelableFlags) {
        dest.writeString(filename);
        dest.writeString(packageName);
        dest.writeInt(version);
        dest.writeInt(flags);
        dest.writeByteArray(salt);
    }

    public static final Parcelable.Creator<ObbInfo> CREATOR = new Parcelable.Creator<ObbInfo>() {
        @Override
        public ObbInfo createFromParcel(Parcel source) {
            return new ObbInfo(source);
        }

        @Override
        public ObbInfo[] newArray(int size) {
            return new ObbInfo[size];
        }
    };
}
