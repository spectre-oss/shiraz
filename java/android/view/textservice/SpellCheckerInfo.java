// SPDX-License-Identifier: GPL-3.0-only

package android.view.textservice;

import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.List;

public final class SpellCheckerInfo implements Parcelable {
    private final ServiceInfo mService;
    private final String mId;
    private final int mLabel;
    private final String mSettingsActivityName;
    private final List<SpellCheckerSubtype> mSubtypes = new ArrayList<SpellCheckerSubtype>();

    SpellCheckerInfo(
            ServiceInfo service,
            String id,
            int label,
            String settingsActivityName,
            List<SpellCheckerSubtype> subtypes) {
        mService = service;
        mId = id;
        mLabel = label;
        mSettingsActivityName = settingsActivityName;
        if (subtypes != null) {
            mSubtypes.addAll(subtypes);
        }
    }

    private SpellCheckerInfo(Parcel source) {
        mLabel = source.readInt();
        mId = source.readString();
        mSettingsActivityName = source.readString();
        if (source.readInt() != 0) {
            ServiceInfo si = new ServiceInfo();
            si.packageName = source.readString();
            si.name = source.readString();
            mService = si;
        } else {
            mService = null;
        }
        source.readTypedList(mSubtypes, SpellCheckerSubtype.CREATOR);
    }

    public String getId() {
        return mId;
    }

    public ComponentName getComponent() {
        if (mService == null) {
            return null;
        }
        return new ComponentName(mService.packageName, mService.name);
    }

    public String getPackageName() {
        return mService != null ? mService.packageName : null;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mLabel);
        dest.writeString(mId);
        dest.writeString(mSettingsActivityName);
        if (mService != null) {
            dest.writeInt(1);
            dest.writeString(mService.packageName);
            dest.writeString(mService.name);
        } else {
            dest.writeInt(0);
        }
        dest.writeTypedList(mSubtypes);
    }

    public CharSequence loadLabel(PackageManager pm) {
        if (mLabel == 0 || pm == null || mService == null) {
            return "";
        }
        CharSequence label = pm.getText(getPackageName(), mLabel, mService.applicationInfo);
        return label != null ? label : "";
    }

    public Drawable loadIcon(PackageManager pm) {
        if (mService != null && pm != null) {
            return mService.loadIcon(pm);
        }
        return null;
    }

    public ServiceInfo getServiceInfo() {
        return mService;
    }

    public String getSettingsActivity() {
        return mSettingsActivityName;
    }

    public int getSubtypeCount() {
        return mSubtypes.size();
    }

    public SpellCheckerSubtype getSubtypeAt(int index) {
        return mSubtypes.get(index);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<SpellCheckerInfo> CREATOR =
            new Parcelable.Creator<SpellCheckerInfo>() {
                @Override
                public SpellCheckerInfo createFromParcel(Parcel source) {
                    return new SpellCheckerInfo(source);
                }

                @Override
                public SpellCheckerInfo[] newArray(int size) {
                    return new SpellCheckerInfo[size];
                }
            };
}
