// SPDX-License-Identifier: GPL-3.0-only
package android.service.chooser;

import android.content.ComponentName;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class ChooserResult implements Parcelable {
    public static final int CHOOSER_RESULT_UNKNOWN = -1;
    public static final int CHOOSER_RESULT_SELECTED_COMPONENT = 0;
    public static final int CHOOSER_RESULT_COPY = 1;
    public static final int CHOOSER_RESULT_EDIT = 2;

    public static final Parcelable.Creator<ChooserResult> CREATOR =
            new Parcelable.Creator<ChooserResult>() {
                @Override
                public ChooserResult createFromParcel(Parcel source) {
                    int type = source.readInt();
                    ComponentName cn = source.readTypedObject(ComponentName.CREATOR);
                    boolean isShortcut = source.readInt() != 0;
                    return new ChooserResult(type, cn, isShortcut);
                }

                @Override
                public ChooserResult[] newArray(int size) {
                    return new ChooserResult[size];
                }
            };

    private final int mType;
    private final ComponentName mSelectedComponent;
    private final boolean mIsShortcut;

    public ChooserResult(int type, ComponentName selectedComponent, boolean isShortcut) {
        mType = type;
        mSelectedComponent = selectedComponent;
        mIsShortcut = isShortcut;
    }

    public int getType() {
        return mType;
    }

    public ComponentName getSelectedComponent() {
        return mSelectedComponent;
    }

    public boolean isShortcut() {
        return mIsShortcut;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mType);
        dest.writeTypedObject(mSelectedComponent, flags);
        dest.writeInt(mIsShortcut ? 1 : 0);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChooserResult)) return false;
        ChooserResult that = (ChooserResult) o;
        return mType == that.mType
                && mIsShortcut == that.mIsShortcut
                && Objects.equals(mSelectedComponent, that.mSelectedComponent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mType, mSelectedComponent, mIsShortcut);
    }
}
