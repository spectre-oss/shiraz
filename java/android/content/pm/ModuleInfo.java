// SPDX-License-Identifier: GPL-3.0-only
package android.content.pm;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import java.util.Objects;

/** Information about a modular system component (Mainline/APEX module). */
public final class ModuleInfo implements Parcelable {
    private CharSequence mName;
    private String mPackageName;
    private boolean mHidden;

    public ModuleInfo() {}

    public ModuleInfo(ModuleInfo orig) {
        mName = orig.mName;
        mPackageName = orig.mPackageName;
        mHidden = orig.mHidden;
    }

    private ModuleInfo(Parcel source) {
        mName = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source);
        mPackageName = source.readString();
        mHidden = source.readInt() != 0;
    }

    public ModuleInfo setName(CharSequence name) {
        mName = name;
        return this;
    }

    public CharSequence getName() {
        return mName;
    }

    public ModuleInfo setPackageName(String packageName) {
        mPackageName = packageName;
        return this;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public ModuleInfo setHidden(boolean hidden) {
        mHidden = hidden;
        return this;
    }

    public boolean isHidden() {
        return mHidden;
    }

    @Override
    public String toString() {
        return "ModuleInfo{" + Integer.toHexString(System.identityHashCode(this))
                + " " + mName + "}";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mName, mPackageName, Boolean.valueOf(mHidden));
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ModuleInfo)) {
            return false;
        }
        ModuleInfo other = (ModuleInfo) obj;
        return Objects.equals(mName, other.mName)
                && Objects.equals(mPackageName, other.mPackageName)
                && mHidden == other.mHidden;
    }

    @Override
    public void writeToParcel(Parcel dest, int parcelableFlags) {
        TextUtils.writeToParcel(mName, dest, parcelableFlags);
        dest.writeString(mPackageName);
        dest.writeInt(mHidden ? 1 : 0);
    }

    public static final Parcelable.Creator<ModuleInfo> CREATOR =
            new Parcelable.Creator<ModuleInfo>() {
                @Override
                public ModuleInfo createFromParcel(Parcel source) {
                    return new ModuleInfo(source);
                }

                @Override
                public ModuleInfo[] newArray(int size) {
                    return new ModuleInfo[size];
                }
            };
}
