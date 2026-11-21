// SPDX-License-Identifier: GPL-3.0-only
package android.service.credentials;

import android.app.slice.Slice;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

/** Account/container destination entry shown in the Create Credential UI. */
public final class CreateEntry implements Parcelable {
    private final Slice mSlice;

    public CreateEntry(Slice slice) {
        this.mSlice = Objects.requireNonNull(slice, "slice must not be null");
    }

    private CreateEntry(Parcel in) {
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

    public static final Parcelable.Creator<CreateEntry> CREATOR =
            new Parcelable.Creator<CreateEntry>() {
                @Override
                public CreateEntry createFromParcel(Parcel in) {
                    return new CreateEntry(in);
                }

                @Override
                public CreateEntry[] newArray(int size) {
                    return new CreateEntry[size];
                }
            };
}
