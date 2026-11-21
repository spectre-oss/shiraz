// SPDX-License-Identifier: GPL-3.0-only
package android.content.pm;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;

/** Representation of a launchable activity entry returned by LauncherApps. */
public class LauncherActivityInfo {
    private final Context mContext;
    private final ActivityInfo mActivityInfo;
    private final ComponentName mComponentName;
    private final UserHandle mUser;
    private final long mFirstInstallTime;

    public LauncherActivityInfo(
            Context context,
            ActivityInfo activityInfo,
            UserHandle user,
            long firstInstallTime) {
        mContext = context;
        mActivityInfo = activityInfo;
        mComponentName = activityInfo != null && activityInfo.packageName != null && activityInfo.name != null
                ? new ComponentName(activityInfo.packageName, activityInfo.name)
                : null;
        mUser = user;
        mFirstInstallTime = firstInstallTime;
    }

    public ComponentName getComponentName() {
        return mComponentName;
    }

    public UserHandle getUser() {
        return mUser;
    }

    public CharSequence getLabel() {
        if (mActivityInfo != null && mContext != null && mContext.getPackageManager() != null) {
            return mActivityInfo.loadLabel(mContext.getPackageManager());
        }
        return mActivityInfo != null && mActivityInfo.nonLocalizedLabel != null
                ? mActivityInfo.nonLocalizedLabel
                : (mComponentName != null ? mComponentName.getClassName() : "");
    }

    public float getLoadingProgress() {
        return 1.0f;
    }

    public Drawable getIcon(int density) {
        if (mActivityInfo != null && mContext != null && mContext.getPackageManager() != null) {
            return mActivityInfo.loadIcon(mContext.getPackageManager());
        }
        return null;
    }

    public ActivityInfo getActivityInfo() {
        return mActivityInfo;
    }

    public ApplicationInfo getApplicationInfo() {
        return mActivityInfo != null ? mActivityInfo.applicationInfo : null;
    }

    public long getFirstInstallTime() {
        return mFirstInstallTime;
    }

    public String getName() {
        return mActivityInfo != null ? mActivityInfo.name : null;
    }

    public Drawable getBadgedIcon(int density) {
        return getIcon(density);
    }
}
