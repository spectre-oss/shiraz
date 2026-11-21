// SPDX-License-Identifier: GPL-3.0-only
package android.net;

import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;

/** Handle passed to captive-portal sign-in activities. */
public class CaptivePortal implements Parcelable {
    private final IBinder mBinder;
    private volatile boolean mDismissed;
    private volatile boolean mIgnored;

    CaptivePortal(IBinder binder) {
        this.mBinder = binder;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeStrongBinder(mBinder);
    }

    public void reportCaptivePortalDismissed() {
        mDismissed = true;
    }

    public void ignoreNetwork() {
        mIgnored = true;
    }

    boolean isDismissed() {
        return mDismissed;
    }

    boolean isIgnored() {
        return mIgnored;
    }

    public static final Parcelable.Creator<CaptivePortal> CREATOR =
            new Parcelable.Creator<CaptivePortal>() {
                @Override
                public CaptivePortal createFromParcel(Parcel in) {
                    return new CaptivePortal(in.readStrongBinder());
                }

                @Override
                public CaptivePortal[] newArray(int size) {
                    return new CaptivePortal[size];
                }
            };
}
