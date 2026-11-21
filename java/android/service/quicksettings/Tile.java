// SPDX-License-Identifier: GPL-3.0-only
package android.service.quicksettings;

import android.app.PendingIntent;
import android.graphics.drawable.Icon;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

public final class Tile implements Parcelable {
    public static final int STATE_UNAVAILABLE = 0;
    public static final int STATE_INACTIVE = 1;
    public static final int STATE_ACTIVE = 2;

    public static final Parcelable.Creator<Tile> CREATOR =
            new Parcelable.Creator<Tile>() {
                @Override
                public Tile createFromParcel(Parcel source) {
                    Tile t = new Tile();
                    t.mIcon = source.readTypedObject(Icon.CREATOR);
                    t.mState = source.readInt();
                    t.mLabel = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source);
                    t.mSubtitle = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source);
                    t.mContentDescription = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source);
                    t.mStateDescription = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source);
                    t.mPendingIntent = source.readTypedObject(PendingIntent.CREATOR);
                    return t;
                }

                @Override
                public Tile[] newArray(int size) {
                    return new Tile[size];
                }
            };

    private Icon mIcon;
    private CharSequence mLabel;
    private CharSequence mSubtitle;
    private CharSequence mContentDescription;
    private CharSequence mStateDescription;
    private PendingIntent mPendingIntent;
    private int mState = STATE_INACTIVE;

    public Tile() {
    }

    public int getState() { return mState; }
    public void setState(int state) { mState = state; }
    public Icon getIcon() { return mIcon; }
    public void setIcon(Icon icon) { mIcon = icon; }
    public CharSequence getLabel() { return mLabel; }
    public void setLabel(CharSequence label) { mLabel = label; }
    public CharSequence getSubtitle() { return mSubtitle; }
    public void setSubtitle(CharSequence subtitle) { mSubtitle = subtitle; }
    public CharSequence getContentDescription() { return mContentDescription; }
    public void setContentDescription(CharSequence contentDescription) { mContentDescription = contentDescription; }
    public CharSequence getStateDescription() { return mStateDescription; }
    public void setStateDescription(CharSequence stateDescription) { mStateDescription = stateDescription; }
    public PendingIntent getActivityLaunchForClick() { return mPendingIntent; }
    public void setActivityLaunchForClick(PendingIntent pendingIntent) { mPendingIntent = pendingIntent; }

    public void updateTile() {
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedObject(mIcon, flags);
        dest.writeInt(mState);
        TextUtils.writeToParcel(mLabel, dest, flags);
        TextUtils.writeToParcel(mSubtitle, dest, flags);
        TextUtils.writeToParcel(mContentDescription, dest, flags);
        TextUtils.writeToParcel(mStateDescription, dest, flags);
        dest.writeTypedObject(mPendingIntent, flags);
    }
}
