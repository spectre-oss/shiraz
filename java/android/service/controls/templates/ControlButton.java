// SPDX-License-Identifier: GPL-3.0-only
package android.service.controls.templates;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import java.util.Objects;

public final class ControlButton implements Parcelable {
    public static final Parcelable.Creator<ControlButton> CREATOR =
            new Parcelable.Creator<ControlButton>() {
                @Override
                public ControlButton createFromParcel(Parcel in) {
                    boolean checked = in.readByte() != 0;
                    CharSequence desc = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
                    return new ControlButton(checked, desc != null ? desc : "");
                }

                @Override
                public ControlButton[] newArray(int size) {
                    return new ControlButton[size];
                }
            };

    private final boolean mChecked;
    private final CharSequence mActionDescription;

    public ControlButton(boolean checked, CharSequence actionDescription) {
        mChecked = checked;
        mActionDescription = Objects.requireNonNull(
                actionDescription, "actionDescription must not be null");
    }

    public boolean isChecked() {
        return mChecked;
    }

    public CharSequence getActionDescription() {
        return mActionDescription;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByte((byte) (mChecked ? 1 : 0));
        TextUtils.writeToParcel(mActionDescription, dest, flags);
    }
}
