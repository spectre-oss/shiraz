// SPDX-License-Identifier: GPL-3.0-only
package android.service.credentials;

import android.app.slice.Slice;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

/** Action entry displayed to the user in the Credential Selector UI. */
public final class Action implements Parcelable {
    private final Slice mSlice;

    public Action(Slice slice) {
        this.mSlice = Objects.requireNonNull(slice, "slice must not be null");
    }

    private Action(Parcel in) {
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

    public static final Parcelable.Creator<Action> CREATOR =
            new Parcelable.Creator<Action>() {
                @Override
                public Action createFromParcel(Parcel in) {
                    return new Action(in);
                }

                @Override
                public Action[] newArray(int size) {
                    return new Action[size];
                }
            };
}
