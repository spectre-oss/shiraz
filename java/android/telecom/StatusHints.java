// SPDX-License-Identifier: GPL-3.0-only
package android.telecom;

import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

/** In-call status label, icon, and extras displayed in the call UI. */
public final class StatusHints implements Parcelable {
    private final CharSequence mLabel;
    private final Icon mIcon;
    private final Bundle mExtras;

    public StatusHints(CharSequence label, Icon icon, Bundle extras) {
        this.mLabel = label;
        this.mIcon = icon;
        this.mExtras = extras == null ? null : new Bundle(extras);
    }

    private StatusHints(Parcel in) {
        this.mLabel = in.readString();
        this.mIcon = PhoneAccountHandle.readIconFromParcel(in);
        this.mExtras = in.readBundle(StatusHints.class.getClassLoader());
    }

    public CharSequence getLabel() {
        return mLabel;
    }

    public Icon getIcon() {
        return mIcon;
    }

    public Bundle getExtras() {
        return mExtras;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(mLabel == null ? null : mLabel.toString());
        PhoneAccountHandle.writeIconToParcel(mIcon, out);
        out.writeBundle(mExtras);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof StatusHints)) {
            return false;
        }
        StatusHints s = (StatusHints) other;
        return Objects.equals(
                        mLabel == null ? null : mLabel.toString(),
                        s.mLabel == null ? null : s.mLabel.toString())
                && PhoneAccountHandle.iconsEqual(mIcon, s.mIcon)
                && ((mExtras == null && s.mExtras == null)
                        || (mExtras != null
                                && s.mExtras != null
                                && mExtras.size() == s.mExtras.size()));
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mLabel == null ? null : mLabel.toString(),
                Integer.valueOf(PhoneAccountHandle.iconHashCode(mIcon)));
    }

    public static final Parcelable.Creator<StatusHints> CREATOR =
            new Parcelable.Creator<StatusHints>() {
                @Override
                public StatusHints createFromParcel(Parcel in) {
                    return new StatusHints(in);
                }

                @Override
                public StatusHints[] newArray(int size) {
                    return new StatusHints[size];
                }
            };
}
