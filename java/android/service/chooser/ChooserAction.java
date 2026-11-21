// SPDX-License-Identifier: GPL-3.0-only
package android.service.chooser;

import android.app.PendingIntent;
import android.graphics.drawable.Icon;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import java.util.Objects;

public final class ChooserAction implements Parcelable {
    public static final Parcelable.Creator<ChooserAction> CREATOR =
            new Parcelable.Creator<ChooserAction>() {
                @Override
                public ChooserAction createFromParcel(Parcel source) {
                    Icon icon = source.readTypedObject(Icon.CREATOR);
                    CharSequence label = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source);
                    PendingIntent action = source.readTypedObject(PendingIntent.CREATOR);
                    return new ChooserAction(icon, label != null ? label : "", action);
                }

                @Override
                public ChooserAction[] newArray(int size) {
                    return new ChooserAction[size];
                }
            };

    private final Icon mIcon;
    private final CharSequence mLabel;
    private final PendingIntent mAction;

    ChooserAction(Icon icon, CharSequence label, PendingIntent action) {
        mIcon = icon;
        mLabel = label;
        mAction = action;
    }

    public CharSequence getLabel() {
        return mLabel;
    }

    public Icon getIcon() {
        return mIcon;
    }

    public PendingIntent getAction() {
        return mAction;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedObject(mIcon, flags);
        TextUtils.writeToParcel(mLabel, dest, flags);
        dest.writeTypedObject(mAction, flags);
    }

    @Override
    public String toString() {
        return "ChooserAction{label=" + mLabel + "}";
    }

    public static final class Builder {
        private final Icon mIcon;
        private final CharSequence mLabel;
        private final PendingIntent mAction;

        public Builder(Icon icon, CharSequence label, PendingIntent action) {
            mIcon = Objects.requireNonNull(icon, "icon must not be null");
            mLabel = Objects.requireNonNull(label, "label must not be null");
            if (label.length() == 0) {
                throw new IllegalArgumentException("label must not be empty");
            }
            mAction = Objects.requireNonNull(action, "action must not be null");
        }

        public ChooserAction build() {
            return new ChooserAction(mIcon, mLabel, mAction);
        }
    }
}
