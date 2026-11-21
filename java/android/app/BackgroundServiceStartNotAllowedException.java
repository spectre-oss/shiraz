// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.os.Parcel;
import android.os.Parcelable;

public final class BackgroundServiceStartNotAllowedException extends ServiceStartNotAllowedException
        implements Parcelable {
    public BackgroundServiceStartNotAllowedException(String message) {
        super(message);
    }

    private BackgroundServiceStartNotAllowedException(Parcel source) {
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

    public static final Parcelable.Creator<BackgroundServiceStartNotAllowedException> CREATOR =
            new Parcelable.Creator<BackgroundServiceStartNotAllowedException>() {
                @Override
                public BackgroundServiceStartNotAllowedException createFromParcel(Parcel source) {
                    return new BackgroundServiceStartNotAllowedException(source);
                }

                @Override
                public BackgroundServiceStartNotAllowedException[] newArray(int size) {
                    return new BackgroundServiceStartNotAllowedException[size];
                }
            };
}
