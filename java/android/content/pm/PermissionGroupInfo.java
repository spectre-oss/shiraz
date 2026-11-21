// SPDX-License-Identifier: GPL-3.0-only
package android.content.pm;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

/** Information retrieved from PackageManager about a logical permission group. */
public class PermissionGroupInfo extends PackageItemInfo implements Parcelable {
    public static final int FLAG_PERSONAL_INFO = 1 << 0;

    public int descriptionRes;
    public CharSequence nonLocalizedDescription;
    public int flags;
    public int priority;

    @Deprecated
    public PermissionGroupInfo() {}

    @Deprecated
    public PermissionGroupInfo(PermissionGroupInfo orig) {
        if (orig != null) {
            name = orig.name;
            packageName = orig.packageName;
            labelRes = orig.labelRes;
            nonLocalizedLabel = orig.nonLocalizedLabel;
            icon = orig.icon;
            logo = orig.logo;
            banner = orig.banner;
            metaData = orig.metaData;
            descriptionRes = orig.descriptionRes;
            nonLocalizedDescription = orig.nonLocalizedDescription;
            flags = orig.flags;
            priority = orig.priority;
        }
    }

    private PermissionGroupInfo(Parcel source) {
        name = source.readString();
        packageName = source.readString();
        labelRes = source.readInt();
        nonLocalizedLabel = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source);
        icon = source.readInt();
        logo = source.readInt();
        banner = source.readInt();
        metaData = source.readBundle();
        descriptionRes = source.readInt();
        nonLocalizedDescription = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source);
        flags = source.readInt();
        priority = source.readInt();
    }

    public CharSequence loadDescription(PackageManager pm) {
        if (nonLocalizedDescription != null) {
            return nonLocalizedDescription;
        }
        if (descriptionRes != 0 && pm != null && packageName != null) {
            CharSequence label = pm.getText(packageName, descriptionRes, null);
            if (label != null) {
                return label;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "PermissionGroupInfo{" + Integer.toHexString(System.identityHashCode(this))
                + " " + name + " flgs=0x" + Integer.toHexString(flags) + "}";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int parcelableFlags) {
        dest.writeString(name);
        dest.writeString(packageName);
        dest.writeInt(labelRes);
        TextUtils.writeToParcel(nonLocalizedLabel, dest, parcelableFlags);
        dest.writeInt(icon);
        dest.writeInt(logo);
        dest.writeInt(banner);
        dest.writeBundle(metaData);
        dest.writeInt(descriptionRes);
        TextUtils.writeToParcel(nonLocalizedDescription, dest, parcelableFlags);
        dest.writeInt(flags);
        dest.writeInt(priority);
    }

    public static final Creator<PermissionGroupInfo> CREATOR = new Creator<PermissionGroupInfo>() {
        @Override
        public PermissionGroupInfo createFromParcel(Parcel source) {
            return new PermissionGroupInfo(source);
        }

        @Override
        public PermissionGroupInfo[] newArray(int size) {
            return new PermissionGroupInfo[size];
        }
    };
}
