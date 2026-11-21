// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.graphics.drawable.Icon;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import java.io.PrintWriter;
import java.util.Objects;

/**
 * Represents a remote action that can be called from another process.
 */
public final class RemoteAction implements Parcelable {
    private final Icon mIcon;
    private final CharSequence mTitle;
    private final CharSequence mContentDescription;
    private final PendingIntent mActionIntent;
    private boolean mEnabled;
    private boolean mShouldShowIcon;

    public RemoteAction(
            Icon icon,
            CharSequence title,
            CharSequence contentDescription,
            PendingIntent intent) {
        mIcon = Objects.requireNonNull(icon, "icon");
        mTitle = Objects.requireNonNull(title, "title");
        mContentDescription = Objects.requireNonNull(contentDescription, "contentDescription");
        mActionIntent = Objects.requireNonNull(intent, "intent");
        mEnabled = true;
        mShouldShowIcon = true;
    }

    RemoteAction(Parcel in) {
        mIcon = Icon.CREATOR.createFromParcel(in);
        mTitle = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
        mContentDescription = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
        mActionIntent = PendingIntent.readPendingIntentOrNullFromParcel(in);
        mEnabled = in.readBoolean();
        mShouldShowIcon = in.readBoolean();
    }

    public void setEnabled(boolean enabled) {
        mEnabled = enabled;
    }

    public boolean isEnabled() {
        return mEnabled;
    }

    public void setShouldShowIcon(boolean shouldShowIcon) {
        mShouldShowIcon = shouldShowIcon;
    }

    public boolean shouldShowIcon() {
        return mShouldShowIcon;
    }

    public Icon getIcon() {
        return mIcon;
    }

    public CharSequence getTitle() {
        return mTitle;
    }

    public CharSequence getContentDescription() {
        return mContentDescription;
    }

    public PendingIntent getActionIntent() {
        return mActionIntent;
    }

    @Override
    public RemoteAction clone() {
        RemoteAction copy = new RemoteAction(mIcon, mTitle, mContentDescription, mActionIntent);
        copy.setEnabled(mEnabled);
        copy.setShouldShowIcon(mShouldShowIcon);
        return copy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RemoteAction)) return false;
        RemoteAction other = (RemoteAction) o;
        return mEnabled == other.mEnabled
                && mShouldShowIcon == other.mShouldShowIcon
                && Objects.equals(mIcon, other.mIcon)
                && TextUtils.equals(mTitle, other.mTitle)
                && TextUtils.equals(mContentDescription, other.mContentDescription)
                && Objects.equals(mActionIntent, other.mActionIntent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mIcon,
                mTitle != null ? mTitle.toString() : null,
                mContentDescription != null ? mContentDescription.toString() : null,
                mActionIntent,
                mEnabled,
                mShouldShowIcon);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        mIcon.writeToParcel(out, 0);
        TextUtils.writeToParcel(mTitle, out, flags);
        TextUtils.writeToParcel(mContentDescription, out, flags);
        PendingIntent.writePendingIntentOrNullToParcel(mActionIntent, out);
        out.writeBoolean(mEnabled);
        out.writeBoolean(mShouldShowIcon);
    }

    public void dump(String prefix, PrintWriter pw) {
        pw.print(prefix);
        pw.print("title=" + mTitle);
        pw.print(" enabled=" + mEnabled);
        pw.print(" contentDescription=" + mContentDescription);
        pw.print(" icon=" + mIcon);
        pw.print(" action=" + mActionIntent);
        pw.println(" shouldShowIcon=" + mShouldShowIcon);
    }

    public static final Parcelable.Creator<RemoteAction> CREATOR =
            new Parcelable.Creator<RemoteAction>() {
                @Override
                public RemoteAction createFromParcel(Parcel in) {
                    return new RemoteAction(in);
                }

                @Override
                public RemoteAction[] newArray(int size) {
                    return new RemoteAction[size];
                }
            };
}
