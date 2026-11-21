// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.os.Parcel;
import android.os.Parcelable;

public final class StartForegroundCalledOnStoppedServiceException extends IllegalStateException
        implements Parcelable {
    public StartForegroundCalledOnStoppedServiceException(String message) {
        super(message);
    }

    private StartForegroundCalledOnStoppedServiceException(Parcel source) {
        super(source.readString());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(getMessage());
    }

    public static final Parcelable.Creator<StartForegroundCalledOnStoppedServiceException> CREATOR =
            new Parcelable.Creator<StartForegroundCalledOnStoppedServiceException>() {
                @Override
                public StartForegroundCalledOnStoppedServiceException createFromParcel(Parcel source) {
                    return new StartForegroundCalledOnStoppedServiceException(source);
                }

                @Override
                public StartForegroundCalledOnStoppedServiceException[] newArray(int size) {
                    return new StartForegroundCalledOnStoppedServiceException[size];
                }
            };
}
