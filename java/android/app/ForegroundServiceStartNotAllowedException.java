// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.os.Parcel;
import android.os.Parcelable;

public final class ForegroundServiceStartNotAllowedException extends ServiceStartNotAllowedException
        implements Parcelable {
    public ForegroundServiceStartNotAllowedException(String message) {
        super(message);
    }

    private ForegroundServiceStartNotAllowedException(Parcel source) {
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

    public static final Parcelable.Creator<ForegroundServiceStartNotAllowedException> CREATOR =
            new Parcelable.Creator<ForegroundServiceStartNotAllowedException>() {
                @Override
                public ForegroundServiceStartNotAllowedException createFromParcel(Parcel source) {
                    return new ForegroundServiceStartNotAllowedException(source);
                }

                @Override
                public ForegroundServiceStartNotAllowedException[] newArray(int size) {
                    return new ForegroundServiceStartNotAllowedException[size];
                }
            };
}
