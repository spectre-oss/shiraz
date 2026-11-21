// SPDX-License-Identifier: GPL-3.0-only
package android.media.tv.interactive;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class TvInteractiveAppServiceInfo implements Parcelable {
    public static final int INTERACTIVE_APP_TYPE_HBBTV = 1;
    public static final int INTERACTIVE_APP_TYPE_ATSC = 2;
    public static final int INTERACTIVE_APP_TYPE_GINGA = 4;
    public static final int INTERACTIVE_APP_TYPE_TARGETED_AD = 8;
    public static final int INTERACTIVE_APP_TYPE_OTHER = 0x80000000;

    private final String mId;
    private final ServiceInfo mService;
    private final int mTypes;
    private final List<String> mExtraTypes;

    public TvInteractiveAppServiceInfo(Context context, ComponentName component) {
        Objects.requireNonNull(context, "context cannot be null.");
        Objects.requireNonNull(component, "component cannot be null.");
        ServiceInfo si = null;
        if (context.getPackageManager() != null) {
            try {
                si = context.getPackageManager().getServiceInfo(
                        component, PackageManager.GET_SERVICES | PackageManager.GET_META_DATA);
            } catch (PackageManager.NameNotFoundException ignored) {
                si = null;
            }
        }
        if (si == null) {
            si = new ServiceInfo();
            si.packageName = component.getPackageName();
            si.name = component.getClassName();
        }
        mService = si;
        mId = component.flattenToShortString();
        mTypes = 0;
        mExtraTypes = new ArrayList<String>();
    }

    private TvInteractiveAppServiceInfo(
            ServiceInfo service, String id, int types, List<String> extraTypes) {
        mService = service;
        mId = id;
        mTypes = types;
        mExtraTypes = extraTypes != null
                ? new ArrayList<String>(extraTypes)
                : new ArrayList<String>();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (mService != null) {
            dest.writeInt(1);
            dest.writeString(mService.packageName);
            dest.writeString(mService.name);
            dest.writeString(mService.permission);
            dest.writeInt(mService.flags);
            dest.writeInt(mService.foregroundServiceType);
        } else {
            dest.writeInt(0);
        }
        dest.writeString(mId);
        dest.writeInt(mTypes);
        dest.writeInt(mExtraTypes.size());
        for (int i = 0; i < mExtraTypes.size(); i++) {
            dest.writeString(mExtraTypes.get(i));
        }
    }

    public String getId() {
        return mId;
    }

    public ServiceInfo getServiceInfo() {
        return mService;
    }

    public int getSupportedTypes() {
        return mTypes;
    }

    public List<String> getCustomSupportedTypes() {
        return Collections.unmodifiableList(mExtraTypes);
    }

    public static final Parcelable.Creator<TvInteractiveAppServiceInfo> CREATOR =
            new Parcelable.Creator<TvInteractiveAppServiceInfo>() {
                @Override
                public TvInteractiveAppServiceInfo createFromParcel(Parcel in) {
                    ServiceInfo si = null;
                    if (in.readInt() != 0) {
                        si = new ServiceInfo();
                        si.packageName = in.readString();
                        si.name = in.readString();
                        si.permission = in.readString();
                        si.flags = in.readInt();
                        si.foregroundServiceType = in.readInt();
                    }
                    String id = in.readString();
                    int types = in.readInt();
                    int count = Math.max(0, in.readInt());
                    List<String> extra = new ArrayList<String>(count);
                    for (int i = 0; i < count; i++) {
                        extra.add(in.readString());
                    }
                    return new TvInteractiveAppServiceInfo(si, id, types, extra);
                }

                @Override
                public TvInteractiveAppServiceInfo[] newArray(int size) {
                    return new TvInteractiveAppServiceInfo[size];
                }
            };
}
