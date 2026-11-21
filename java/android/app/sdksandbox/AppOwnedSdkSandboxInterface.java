// SPDX-License-Identifier: GPL-3.0-only
package android.app.sdksandbox;

import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;

public final class AppOwnedSdkSandboxInterface implements Parcelable {
    private final String mName;
    private final long mVersion;
    private final IBinder mInterface;

    public AppOwnedSdkSandboxInterface(String name, long version, IBinder binder) {
        if (name == null) throw new NullPointerException("name");
        if (binder == null) throw new NullPointerException("binder");
        mName = name;
        mVersion = version;
        mInterface = binder;
    }

    AppOwnedSdkSandboxInterface(Parcel in) {
        mName = in.readString();
        mVersion = in.readLong();
        mInterface = in.readStrongBinder();
    }

    public String getName() {
        return mName;
    }

    public long getVersion() {
        return mVersion;
    }

    public IBinder getInterface() {
        return mInterface;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mName);
        dest.writeLong(mVersion);
        dest.writeStrongBinder(mInterface);
    }

    public static final Parcelable.Creator<AppOwnedSdkSandboxInterface> CREATOR =
            new Parcelable.Creator<AppOwnedSdkSandboxInterface>() {
                @Override
                public AppOwnedSdkSandboxInterface createFromParcel(Parcel in) {
                    return new AppOwnedSdkSandboxInterface(in);
                }

                @Override
                public AppOwnedSdkSandboxInterface[] newArray(int size) {
                    return new AppOwnedSdkSandboxInterface[size];
                }
            };
}
