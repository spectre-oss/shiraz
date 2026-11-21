// SPDX-License-Identifier: GPL-3.0-only
package android.content.pm;

import android.app.PendingIntent;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.LocusId;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Process;
import android.os.UserHandle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

/** System service providing launcher activity discovery, shortcut queries, and installer callbacks. */
public class LauncherApps {
    public static final String ACTION_CONFIRM_PIN_SHORTCUT =
            "android.content.pm.action.CONFIRM_PIN_SHORTCUT";
    public static final String ACTION_CONFIRM_PIN_APPWIDGET =
            "android.content.pm.action.CONFIRM_PIN_APPWIDGET";
    public static final String EXTRA_PIN_ITEM_REQUEST =
            "android.content.pm.extra.PIN_ITEM_REQUEST";

    private final Context mContext;
    private final Map<Callback, Handler> mCallbacks = new LinkedHashMap<Callback, Handler>();

    public LauncherApps() {
        this(null);
    }

    public LauncherApps(Context context) {
        mContext = context;
    }

    public List<UserHandle> getProfiles() {
        return Collections.singletonList(Process.myUserHandle());
    }

    public List<LauncherActivityInfo> getActivityList(String packageName, UserHandle user) {
        if (mContext == null || mContext.getPackageManager() == null) {
            return Collections.emptyList();
        }
        Intent mainIntent = new Intent(Intent.ACTION_MAIN);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        if (packageName != null) {
            mainIntent.setPackage(packageName);
        }
        List<ResolveInfo> resolved = mContext.getPackageManager().queryIntentActivities(mainIntent, 0);
        if (resolved == null || resolved.isEmpty()) {
            return Collections.emptyList();
        }
        List<LauncherActivityInfo> out = new ArrayList<LauncherActivityInfo>(resolved.size());
        for (ResolveInfo ri : resolved) {
            if (ri != null && ri.activityInfo != null) {
                out.add(new LauncherActivityInfo(mContext, ri.activityInfo, user, 0L));
            }
        }
        return out;
    }

    public final LauncherUserInfo getLauncherUserInfo(UserHandle userHandle) {
        Objects.requireNonNull(userHandle);
        return new LauncherUserInfo("android.os.usertype.full.SYSTEM", 0);
    }

    public IntentSender getAppMarketActivityIntent(String packageName, UserHandle user) {
        return null;
    }

    public List<String> getPreInstalledSystemPackages(UserHandle userHandle) {
        Objects.requireNonNull(userHandle);
        return Collections.emptyList();
    }

    public LauncherActivityInfo resolveActivity(Intent intent, UserHandle user) {
        Objects.requireNonNull(intent);
        if (mContext == null || mContext.getPackageManager() == null) {
            return null;
        }
        ResolveInfo ri = mContext.getPackageManager().resolveActivity(intent, 0);
        if (ri != null && ri.activityInfo != null) {
            return new LauncherActivityInfo(mContext, ri.activityInfo, user, 0L);
        }
        return null;
    }

    public void startMainActivity(
            ComponentName component, UserHandle user, Rect sourceBounds, Bundle opts) {
        Objects.requireNonNull(component);
        if (mContext != null) {
            Intent launch = new Intent(Intent.ACTION_MAIN);
            launch.addCategory(Intent.CATEGORY_LAUNCHER);
            launch.setComponent(component);
            launch.setSourceBounds(sourceBounds);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(launch, opts);
        }
    }

    public void startPackageInstallerSessionDetailsActivity(
            PackageInstaller.SessionInfo sessionInfo, Rect sourceBounds, Bundle opts) {
        Objects.requireNonNull(sessionInfo);
    }

    public void startAppDetailsActivity(
            ComponentName component, UserHandle user, Rect sourceBounds, Bundle opts) {
        Objects.requireNonNull(component);
    }

    public PendingIntent getShortcutIntent(
            String packageName, String shortcutId, Bundle opts, UserHandle user) {
        return null;
    }

    public List<LauncherActivityInfo> getShortcutConfigActivityList(
            String packageName, UserHandle user) {
        return Collections.emptyList();
    }

    public IntentSender getShortcutConfigActivityIntent(LauncherActivityInfo info) {
        Objects.requireNonNull(info);
        return null;
    }

    public boolean isPackageEnabled(String packageName, UserHandle user) {
        if (mContext == null || mContext.getPackageManager() == null) {
            return false;
        }
        try {
            ApplicationInfo ai = mContext.getPackageManager().getApplicationInfo(packageName, 0);
            return ai != null && ai.enabled;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public Bundle getSuspendedPackageLauncherExtras(String packageName, UserHandle user) {
        return null;
    }

    public boolean shouldHideFromSuggestions(String packageName, UserHandle user) {
        return false;
    }

    public ApplicationInfo getApplicationInfo(String packageName, int flags, UserHandle user)
            throws PackageManager.NameNotFoundException {
        if (mContext == null || mContext.getPackageManager() == null) {
            throw new PackageManager.NameNotFoundException(packageName);
        }
        return mContext.getPackageManager().getApplicationInfo(packageName, flags);
    }

    public boolean isActivityEnabled(ComponentName component, UserHandle user) {
        Objects.requireNonNull(component);
        if (mContext == null || mContext.getPackageManager() == null) {
            return false;
        }
        Intent query = new Intent().setComponent(component);
        return mContext.getPackageManager().resolveActivity(query, 0) != null;
    }

    public boolean hasShortcutHostPermission() {
        return false;
    }

    public List<ShortcutInfo> getShortcuts(ShortcutQuery query, UserHandle user) {
        Objects.requireNonNull(query);
        Objects.requireNonNull(user);
        return Collections.emptyList();
    }

    public void pinShortcuts(String packageName, List<String> shortcutIds, UserHandle user) {
        Objects.requireNonNull(packageName);
        Objects.requireNonNull(shortcutIds);
        Objects.requireNonNull(user);
    }

    public Drawable getShortcutIconDrawable(ShortcutInfo shortcut, int density) {
        return null;
    }

    public Drawable getShortcutBadgedIconDrawable(ShortcutInfo shortcut, int density) {
        return getShortcutIconDrawable(shortcut, density);
    }

    public void startShortcut(
            String packageName, String shortcutId, Rect sourceBounds, Bundle startActivityOptions,
            UserHandle user) {
        Objects.requireNonNull(packageName);
        Objects.requireNonNull(shortcutId);
    }

    public void startShortcut(
            ShortcutInfo shortcut, Rect sourceBounds, Bundle startActivityOptions) {
        Objects.requireNonNull(shortcut);
    }

    public void registerCallback(Callback callback) {
        registerCallback(callback, null);
    }

    public synchronized void registerCallback(Callback callback, Handler handler) {
        Objects.requireNonNull(callback);
        mCallbacks.put(callback, handler != null ? handler : new Handler(Looper.getMainLooper()));
    }

    public synchronized void unregisterCallback(Callback callback) {
        mCallbacks.remove(callback);
    }

    public void setArchiveCompatibility(ArchiveCompatibilityParams params) {
        Objects.requireNonNull(params);
    }

    public void registerPackageInstallerSessionCallback(
            final Executor executor, final PackageInstaller.SessionCallback callback) {
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);
        if (mContext != null && mContext.getPackageManager() != null) {
            mContext.getPackageManager().getPackageInstaller().registerSessionCallback(callback);
        }
    }

    public void unregisterPackageInstallerSessionCallback(
            PackageInstaller.SessionCallback callback) {
        Objects.requireNonNull(callback);
        if (mContext != null && mContext.getPackageManager() != null) {
            mContext.getPackageManager().getPackageInstaller().unregisterSessionCallback(callback);
        }
    }

    public List<PackageInstaller.SessionInfo> getAllPackageInstallerSessions() {
        if (mContext != null && mContext.getPackageManager() != null) {
            return mContext.getPackageManager().getPackageInstaller().getAllSessions();
        }
        return Collections.emptyList();
    }

    public PinItemRequest getPinItemRequest(Intent intent) {
        if (intent == null) {
            return null;
        }
        return intent.getParcelableExtra(EXTRA_PIN_ITEM_REQUEST);
    }

    public static class ArchiveCompatibilityParams {
        private boolean mEnableIconOverlay = true;
        private boolean mEnableUnarchivalConfirmation = true;

        public ArchiveCompatibilityParams() {}

        public void setEnableIconOverlay(boolean enableIconOverlay) {
            mEnableIconOverlay = enableIconOverlay;
        }

        public void setEnableUnarchivalConfirmation(boolean enableUnarchivalConfirmation) {
            mEnableUnarchivalConfirmation = enableUnarchivalConfirmation;
        }
    }

    public abstract static class Callback {
        public Callback() {}

        public abstract void onPackageRemoved(String packageName, UserHandle user);

        public abstract void onPackageAdded(String packageName, UserHandle user);

        public abstract void onPackageChanged(String packageName, UserHandle user);

        public abstract void onPackagesAvailable(
                String[] packageNames, UserHandle user, boolean replacing);

        public abstract void onPackagesUnavailable(
                String[] packageNames, UserHandle user, boolean replacing);

        public void onPackagesSuspended(String[] packageNames, UserHandle user) {}

        @Deprecated
        public void onPackagesSuspended(
                String[] packageNames, UserHandle user, Bundle launcherExtras) {
            onPackagesSuspended(packageNames, user);
        }

        public void onPackagesUnsuspended(String[] packageNames, UserHandle user) {}

        public void onShortcutsChanged(
                String packageName, List<ShortcutInfo> shortcuts, UserHandle user) {}

        public void onPackageLoadingProgressChanged(
                String packageName, UserHandle user, float progress) {}
    }

    public static final class PinItemRequest implements Parcelable {
        public static final int REQUEST_TYPE_SHORTCUT = 1;
        public static final int REQUEST_TYPE_APPWIDGET = 2;

        private final int mRequestType;
        private final ShortcutInfo mShortcutInfo;
        private final AppWidgetProviderInfo mAppWidgetProviderInfo;
        private final Bundle mExtras;
        private boolean mAccepted;

        public PinItemRequest(
                int requestType,
                ShortcutInfo shortcutInfo,
                AppWidgetProviderInfo appWidgetInfo,
                Bundle extras) {
            mRequestType = requestType;
            mShortcutInfo = shortcutInfo;
            mAppWidgetProviderInfo = appWidgetInfo;
            mExtras = extras;
        }

        PinItemRequest(Parcel in) {
            mRequestType = in.readInt();
            mShortcutInfo = in.readTypedObject(ShortcutInfo.CREATOR);
            mAppWidgetProviderInfo = in.readTypedObject(AppWidgetProviderInfo.CREATOR);
            mExtras = in.readBundle();
            mAccepted = in.readInt() != 0;
        }

        public int getRequestType() {
            return mRequestType;
        }

        public ShortcutInfo getShortcutInfo() {
            return mShortcutInfo;
        }

        public AppWidgetProviderInfo getAppWidgetProviderInfo(Context context) {
            return mAppWidgetProviderInfo;
        }

        public Bundle getExtras() {
            return mExtras;
        }

        public boolean isValid() {
            return !mAccepted;
        }

        public boolean accept(Bundle options) {
            if (mAccepted) {
                throw new IllegalStateException("Request already accepted");
            }
            mAccepted = true;
            return true;
        }

        public boolean accept() {
            return accept(null);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mRequestType);
            dest.writeTypedObject(mShortcutInfo, flags);
            dest.writeTypedObject(mAppWidgetProviderInfo, flags);
            dest.writeBundle(mExtras);
            dest.writeInt(mAccepted ? 1 : 0);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<PinItemRequest> CREATOR = new Creator<PinItemRequest>() {
            @Override
            public PinItemRequest createFromParcel(Parcel source) {
                return new PinItemRequest(source);
            }

            @Override
            public PinItemRequest[] newArray(int size) {
                return new PinItemRequest[size];
            }
        };
    }

    public static class ShortcutQuery {
        public static final int FLAG_MATCH_DYNAMIC = 1 << 0;
        public static final int FLAG_MATCH_PINNED = 1 << 1;
        public static final int FLAG_GET_KEY_FIELDS_ONLY = 1 << 2;
        public static final int FLAG_MATCH_MANIFEST = 1 << 3;
        public static final int FLAG_MATCH_CACHED = 1 << 4;
        public static final int FLAG_MATCH_PINNED_BY_ANY_LAUNCHER = 1 << 10;

        long mChangedSince;
        String mPackage;
        List<String> mShortcutIds;
        List<LocusId> mLocusIds;
        ComponentName mActivity;
        int mQueryFlags;

        public ShortcutQuery() {}

        public ShortcutQuery setChangedSince(long changedSince) {
            mChangedSince = changedSince;
            return this;
        }

        public ShortcutQuery setPackage(String packageName) {
            mPackage = packageName;
            return this;
        }

        public ShortcutQuery setShortcutIds(List<String> shortcutIds) {
            mShortcutIds = shortcutIds;
            return this;
        }

        public ShortcutQuery setLocusIds(List<LocusId> locusIds) {
            mLocusIds = locusIds;
            return this;
        }

        public ShortcutQuery setActivity(ComponentName activity) {
            mActivity = activity;
            return this;
        }

        public ShortcutQuery setQueryFlags(int queryFlags) {
            mQueryFlags = queryFlags;
            return this;
        }
    }
}
