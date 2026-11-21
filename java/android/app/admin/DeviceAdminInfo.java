// SPDX-License-Identifier: GPL-3.0-only
package android.app.admin;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Printer;
import java.io.IOException;
import org.xmlpull.v1.XmlPullParserException;

public final class DeviceAdminInfo implements Parcelable {
    public static final int USES_POLICY_LIMIT_PASSWORD = 0;
    public static final int USES_POLICY_WATCH_LOGIN = 1;
    public static final int USES_POLICY_RESET_PASSWORD = 2;
    public static final int USES_POLICY_FORCE_LOCK = 3;
    public static final int USES_POLICY_WIPE_DATA = 4;
    public static final int USES_POLICY_EXPIRE_PASSWORD = 6;
    public static final int USES_ENCRYPTED_STORAGE = 7;
    public static final int USES_POLICY_DISABLE_CAMERA = 8;
    public static final int USES_POLICY_DISABLE_KEYGUARD_FEATURES = 9;

    public static final int HEADLESS_DEVICE_OWNER_MODE_UNSUPPORTED = 0;
    public static final int HEADLESS_DEVICE_OWNER_MODE_AFFILIATED = 1;
    public static final int HEADLESS_DEVICE_OWNER_MODE_SINGLE_USER = 2;

    private final ActivityInfo mActivityInfo;
    private boolean mVisible = true;
    private int mUsesPolicies;
    private boolean mSupportsTransferOwnership;
    private int mHeadlessDeviceOwnerMode = HEADLESS_DEVICE_OWNER_MODE_UNSUPPORTED;

    public DeviceAdminInfo(Context context, ResolveInfo resolveInfo)
            throws XmlPullParserException, IOException {
        if (resolveInfo == null || resolveInfo.activityInfo == null) {
            throw new IllegalArgumentException("ResolveInfo must contain activityInfo");
        }
        mActivityInfo = resolveInfo.activityInfo;
    }

    DeviceAdminInfo(Parcel source) {
        mActivityInfo = ActivityInfo.CREATOR.createFromParcel(source);
        mUsesPolicies = source.readInt();
        mSupportsTransferOwnership = source.readInt() != 0;
        mHeadlessDeviceOwnerMode = source.readInt();
    }

    public String getPackageName() {
        return mActivityInfo.packageName;
    }

    public String getReceiverName() {
        return mActivityInfo.name;
    }

    public ActivityInfo getActivityInfo() {
        return mActivityInfo;
    }

    public ComponentName getComponent() {
        return new ComponentName(mActivityInfo.packageName, mActivityInfo.name);
    }

    public CharSequence loadLabel(PackageManager pm) {
        return mActivityInfo.loadLabel(pm);
    }

    public CharSequence loadDescription(PackageManager pm) throws Resources.NotFoundException {
        if (mActivityInfo.descriptionRes != 0 && pm != null && mActivityInfo.applicationInfo != null) {
            try {
                CharSequence text = pm.getResourcesForApplication(mActivityInfo.applicationInfo)
                        .getText(mActivityInfo.descriptionRes);
                if (text != null) {
                    return text;
                }
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        throw new Resources.NotFoundException();
    }

    public Drawable loadIcon(PackageManager pm) {
        return mActivityInfo.loadIcon(pm);
    }

    public boolean isVisible() {
        return mVisible;
    }

    public boolean usesPolicy(int policyIdent) {
        return (mUsesPolicies & (1 << policyIdent)) != 0;
    }

    public String getTagForPolicy(int policyIdent) {
        switch (policyIdent) {
            case USES_POLICY_LIMIT_PASSWORD: return "limit-password";
            case USES_POLICY_WATCH_LOGIN: return "watch-login";
            case USES_POLICY_RESET_PASSWORD: return "reset-password";
            case USES_POLICY_FORCE_LOCK: return "force-lock";
            case USES_POLICY_WIPE_DATA: return "wipe-data";
            case USES_POLICY_EXPIRE_PASSWORD: return "expire-password";
            case USES_ENCRYPTED_STORAGE: return "encrypted-storage";
            case USES_POLICY_DISABLE_CAMERA: return "disable-camera";
            case USES_POLICY_DISABLE_KEYGUARD_FEATURES: return "disable-keyguard-features";
            default: return null;
        }
    }

    public boolean supportsTransferOwnership() {
        return mSupportsTransferOwnership;
    }

    public int getHeadlessDeviceOwnerMode() {
        return mHeadlessDeviceOwnerMode;
    }

    public void dump(Printer pw, String prefix) {
        if (pw != null) {
            pw.println(prefix + "Receiver: " + getComponent());
        }
    }

    @Override
    public String toString() {
        return "DeviceAdminInfo{" + mActivityInfo.name + "}";
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        mActivityInfo.writeToParcel(dest, flags);
        dest.writeInt(mUsesPolicies);
        dest.writeInt(mSupportsTransferOwnership ? 1 : 0);
        dest.writeInt(mHeadlessDeviceOwnerMode);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<DeviceAdminInfo> CREATOR =
            new Parcelable.Creator<DeviceAdminInfo>() {
                @Override
                public DeviceAdminInfo createFromParcel(Parcel source) {
                    return new DeviceAdminInfo(source);
                }

                @Override
                public DeviceAdminInfo[] newArray(int size) {
                    return new DeviceAdminInfo[size];
                }
            };
}
