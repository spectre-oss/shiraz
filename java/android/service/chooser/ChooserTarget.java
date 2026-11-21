// SPDX-License-Identifier: GPL-3.0-only
package android.service.chooser;

import android.content.ComponentName;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

public final class ChooserTarget implements Parcelable {
    public static final Parcelable.Creator<ChooserTarget> CREATOR =
            new Parcelable.Creator<ChooserTarget>() {
                @Override
                public ChooserTarget createFromParcel(Parcel source) {
                    CharSequence title = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source);
                    Icon icon = source.readTypedObject(Icon.CREATOR);
                    float score = source.readFloat();
                    ComponentName cn = source.readTypedObject(ComponentName.CREATOR);
                    Bundle extras = source.readBundle();
                    return new ChooserTarget(title, icon, score, cn, extras);
                }

                @Override
                public ChooserTarget[] newArray(int size) {
                    return new ChooserTarget[size];
                }
            };

    private final CharSequence mTitle;
    private final Icon mIcon;
    private final float mScore;
    private final ComponentName mComponentName;
    private final Bundle mIntentExtras;

    public ChooserTarget(CharSequence title, Icon icon, float score, ComponentName componentName,
            Bundle intentExtras) {
        if (score < 0.0f || score > 1.0f) {
            throw new IllegalArgumentException("score must be between 0.0f and 1.0f");
        }
        mTitle = title;
        mIcon = icon;
        mScore = score;
        mComponentName = componentName;
        mIntentExtras = intentExtras;
    }

    public CharSequence getTitle() {
        return mTitle;
    }

    public Icon getIcon() {
        return mIcon;
    }

    public float getScore() {
        return mScore;
    }

    public ComponentName getComponentName() {
        return mComponentName;
    }

    public Bundle getIntentExtras() {
        return mIntentExtras;
    }

    @Override
    public String toString() {
        return "ChooserTarget{title=" + mTitle + ", component=" + mComponentName + ", score=" + mScore + "}";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        TextUtils.writeToParcel(mTitle, dest, flags);
        dest.writeTypedObject(mIcon, flags);
        dest.writeFloat(mScore);
        dest.writeTypedObject(mComponentName, flags);
        dest.writeBundle(mIntentExtras);
    }
}
