// SPDX-License-Identifier: GPL-3.0-only
package android.app.sdksandbox;

import android.content.pm.SharedLibraryInfo;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;

public final class SandboxedSdk implements Parcelable {
    private final IBinder mInterface;
    private SharedLibraryInfo mSharedLibraryInfo;

    public SandboxedSdk(IBinder sdkInterface) {
        if (sdkInterface == null) throw new NullPointerException("sdkInterface");
        mInterface = sdkInterface;
    }

    SandboxedSdk(Parcel in) {
        mInterface = in.readStrongBinder();
        mSharedLibraryInfo = in.readTypedObject(SharedLibraryInfo.CREATOR);
    }

    public IBinder getInterface() {
        return mInterface;
    }

    public SharedLibraryInfo getSharedLibraryInfo() {
        if (mSharedLibraryInfo == null) {
            throw new IllegalStateException("SharedLibraryInfo unavailable");
        }
        return mSharedLibraryInfo;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeStrongBinder(mInterface);
        dest.writeTypedObject(mSharedLibraryInfo, flags);
    }

    public static final Parcelable.Creator<SandboxedSdk> CREATOR =
            new Parcelable.Creator<SandboxedSdk>() {
                @Override
                public SandboxedSdk createFromParcel(Parcel in) {
                    return new SandboxedSdk(in);
                }

                @Override
                public SandboxedSdk[] newArray(int size) {
                    return new SandboxedSdk[size];
                }
            };
}
