// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.pdf.models;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class ListItem implements Parcelable {
    private final String mLabel;
    private final boolean mSelected;

    public ListItem(String label, boolean selected) {
        this.mLabel = Objects.requireNonNull(label, "Label cannot be null");
        this.mSelected = selected;
    }

    public String getLabel() {
        return mLabel;
    }

    public boolean isSelected() {
        return mSelected;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mLabel, Boolean.valueOf(mSelected));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ListItem)) {
            return false;
        }
        ListItem other = (ListItem) obj;
        return mSelected == other.mSelected && Objects.equals(mLabel, other.mLabel);
    }

    @Override
    public String toString() {
        return "ListItem{label=" + mLabel + ", selected=" + mSelected + "}";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Objects.requireNonNull(dest, "dest");
        dest.writeString(mLabel);
        dest.writeBoolean(mSelected);
    }

    public static final Parcelable.Creator<ListItem> CREATOR =
            new Parcelable.Creator<ListItem>() {
                @Override
                public ListItem createFromParcel(Parcel in) {
                    return new ListItem(in.readString(), in.readBoolean());
                }

                @Override
                public ListItem[] newArray(int size) {
                    return new ListItem[size];
                }
            };
}
