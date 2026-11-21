// SPDX-License-Identifier: GPL-3.0-only
package android.view;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Controls access permissions for content URIs associated with a {@link DragEvent}.
 */
public final class DragAndDropPermissions implements Parcelable {
    private boolean mReleased;

    public DragAndDropPermissions() {
    }

    DragAndDropPermissions(Parcel in) {
        mReleased = in.readBoolean();
    }

    public void release() {
        mReleased = true;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel destination, int flags) {
        destination.writeBoolean(mReleased);
    }

    public static final Parcelable.Creator<DragAndDropPermissions> CREATOR =
            new Parcelable.Creator<DragAndDropPermissions>() {
                @Override
                public DragAndDropPermissions createFromParcel(Parcel source) {
                    return new DragAndDropPermissions(source);
                }

                @Override
                public DragAndDropPermissions[] newArray(int size) {
                    return new DragAndDropPermissions[size];
                }
            };
}
