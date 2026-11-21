// SPDX-License-Identifier: GPL-3.0-only
package android.service.credentials;

import android.app.slice.Slice;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

/** Entry representing a remote device credential flow in the Credential Selector UI. */
public final class RemoteEntry implements Parcelable {
    private final Slice mSlice;

    public RemoteEntry(Slice slice) {
        this.mSlice = Objects.requireNonNull(slice, "slice must not be null");
    }

    private RemoteEntry(Parcel in) {
        this.mSlice =
                Objects.requireNonNull(
                        in.readTypedObject(Slice.CREATOR), "slice must not be null");
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedObject(mSlice, flags);
    }

    public Slice getSlice() {
        return mSlice;
    }

    public static final Parcelable.Creator<RemoteEntry> CREATOR =
            new Parcelable.Creator<RemoteEntry>() {
                @Override
                public RemoteEntry createFromParcel(Parcel in) {
                    return new RemoteEntry(in);
                }

                @Override
                public RemoteEntry[] newArray(int size) {
                    return new RemoteEntry[size];
                }
            };
}
