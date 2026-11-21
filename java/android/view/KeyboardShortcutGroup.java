// SPDX-License-Identifier: GPL-3.0-only
package android.view;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A group of {@link KeyboardShortcutInfo}.
 */
public final class KeyboardShortcutGroup implements Parcelable {
    private final CharSequence mLabel;
    private final List<KeyboardShortcutInfo> mItems;

    public KeyboardShortcutGroup(CharSequence label, List<KeyboardShortcutInfo> items) {
        mLabel = label;
        mItems = new ArrayList<KeyboardShortcutInfo>(Objects.requireNonNull(items, "items"));
    }

    public KeyboardShortcutGroup(CharSequence label) {
        mLabel = label;
        mItems = new ArrayList<KeyboardShortcutInfo>();
    }

    KeyboardShortcutGroup(Parcel source) {
        mLabel = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source);
        ArrayList<KeyboardShortcutInfo> read = source.createTypedArrayList(KeyboardShortcutInfo.CREATOR);
        mItems = read != null ? read : new ArrayList<KeyboardShortcutInfo>();
    }

    public CharSequence getLabel() {
        return mLabel;
    }

    public List<KeyboardShortcutInfo> getItems() {
        return Collections.unmodifiableList(mItems);
    }

    public void addItem(KeyboardShortcutInfo item) {
        mItems.add(Objects.requireNonNull(item, "item"));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        TextUtils.writeToParcel(mLabel, dest, flags);
        dest.writeTypedList(mItems);
    }

    public static final Parcelable.Creator<KeyboardShortcutGroup> CREATOR =
            new Parcelable.Creator<KeyboardShortcutGroup>() {
                @Override
                public KeyboardShortcutGroup createFromParcel(Parcel source) {
                    return new KeyboardShortcutGroup(source);
                }

                @Override
                public KeyboardShortcutGroup[] newArray(int size) {
                    return new KeyboardShortcutGroup[size];
                }
            };
}
