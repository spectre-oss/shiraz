// SPDX-License-Identifier: GPL-3.0-only
package android.appwidget;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.os.UserHandle;
import android.widget.RemoteViews;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Desktop widget hosting is unavailable externally; local host/provider state is tracked in-memory. */
public class AppWidgetManager {
    public static final String ACTION_APPWIDGET_PICK =
            "android.appwidget.action.APPWIDGET_PICK";
    public static final String ACTION_APPWIDGET_BIND =
            "android.appwidget.action.APPWIDGET_BIND";
    public static final String ACTION_APPWIDGET_CONFIGURE =
            "android.appwidget.action.APPWIDGET_CONFIGURE";
    public static final String ACTION_APPWIDGET_UPDATE =
            "android.appwidget.action.APPWIDGET_UPDATE";
    public static final String ACTION_APPWIDGET_OPTIONS_CHANGED =
            "android.appwidget.action.APPWIDGET_UPDATE_OPTIONS";
    public static final String ACTION_APPWIDGET_DELETED =
            "android.appwidget.action.APPWIDGET_DELETED";
    public static final String ACTION_APPWIDGET_DISABLED =
            "android.appwidget.action.APPWIDGET_DISABLED";
    public static final String ACTION_APPWIDGET_ENABLED =
            "android.appwidget.action.APPWIDGET_ENABLED";
    public static final String ACTION_APPWIDGET_RESTORED =
            "android.appwidget.action.APPWIDGET_RESTORED";
    public static final String ACTION_APPWIDGET_HOST_RESTORED =
            "android.appwidget.action.APPWIDGET_HOST_RESTORED";

    public static final String EXTRA_APPWIDGET_ID = "appWidgetId";
    public static final String EXTRA_APPWIDGET_IDS = "appWidgetIds";
    public static final String EXTRA_APPWIDGET_OLD_IDS = "appWidgetOldIds";
    public static final String EXTRA_APPWIDGET_OPTIONS = "appWidgetOptions";
    public static final String EXTRA_APPWIDGET_PREVIEW = "appWidgetPreview";
    public static final String EXTRA_APPWIDGET_PROVIDER = "appWidgetProvider";
    public static final String EXTRA_APPWIDGET_PROVIDER_PROFILE = "appWidgetProviderProfile";
    public static final String EXTRA_CUSTOM_EXTRAS = "customExtras";
    public static final String EXTRA_CUSTOM_INFO = "customInfo";
    public static final String EXTRA_HOST_ID = "hostId";

    public static final int INVALID_APPWIDGET_ID = 0;

    public static final String META_DATA_APPWIDGET_PROVIDER = "android.appwidget.provider";

    public static final String OPTION_APPWIDGET_MIN_WIDTH = "appWidgetMinWidth";
    public static final String OPTION_APPWIDGET_MIN_HEIGHT = "appWidgetMinHeight";
    public static final String OPTION_APPWIDGET_MAX_WIDTH = "appWidgetMaxWidth";
    public static final String OPTION_APPWIDGET_MAX_HEIGHT = "appWidgetMaxHeight";
    public static final String OPTION_APPWIDGET_HOST_CATEGORY = "appWidgetCategory";
    public static final String OPTION_APPWIDGET_RESTORE_COMPLETED =
            "appWidgetRestoreCompleted";
    public static final String OPTION_APPWIDGET_SIZES = "appWidgetSizes";

    private static final AppWidgetManager INSTANCE = new AppWidgetManager();

    private final Map<Integer, ComponentName> mBoundProviders =
            new HashMap<Integer, ComponentName>();
    private final Map<Integer, AppWidgetProviderInfo> mWidgetInfos =
            new HashMap<Integer, AppWidgetProviderInfo>();
    private final Map<Integer, Bundle> mWidgetOptions = new HashMap<Integer, Bundle>();
    private final Map<Integer, RemoteViews> mWidgetViews = new HashMap<Integer, RemoteViews>();
    private final Map<String, RemoteViews> mWidgetPreviews = new HashMap<String, RemoteViews>();

    private AppWidgetManager() {
    }

    public static AppWidgetManager getInstance(Context context) {
        if (context == null) {
            throw new NullPointerException("context");
        }
        return INSTANCE;
    }

    public synchronized void updateAppWidget(int[] appWidgetIds, RemoteViews views) {
        if (appWidgetIds == null) {
            return;
        }
        for (int i = 0; i < appWidgetIds.length; i++) {
            updateAppWidget(appWidgetIds[i], views);
        }
    }

    public synchronized void updateAppWidgetOptions(int appWidgetId, Bundle options) {
        Bundle copy = options != null ? new Bundle(options) : new Bundle();
        mWidgetOptions.put(Integer.valueOf(appWidgetId), copy);
    }

    public synchronized Bundle getAppWidgetOptions(int appWidgetId) {
        Bundle existing = mWidgetOptions.get(Integer.valueOf(appWidgetId));
        return existing != null ? new Bundle(existing) : new Bundle();
    }

    public synchronized void updateAppWidget(int appWidgetId, RemoteViews views) {
        mWidgetViews.put(Integer.valueOf(appWidgetId), views);
        AppWidgetHost.dispatchRemoteViewsUpdate(appWidgetId, views);
    }

    public synchronized void partiallyUpdateAppWidget(int[] appWidgetIds, RemoteViews views) {
        if (appWidgetIds == null) {
            return;
        }
        for (int i = 0; i < appWidgetIds.length; i++) {
            partiallyUpdateAppWidget(appWidgetIds[i], views);
        }
    }

    public synchronized void partiallyUpdateAppWidget(int appWidgetId, RemoteViews views) {
        if (views != null) {
            mWidgetViews.put(Integer.valueOf(appWidgetId), views);
            AppWidgetHost.dispatchRemoteViewsUpdate(appWidgetId, views);
        }
    }

    public synchronized void updateAppWidget(ComponentName provider, RemoteViews views) {
        if (provider == null) {
            throw new IllegalArgumentException("provider cannot be null");
        }
        int[] ids = getAppWidgetIds(provider);
        updateAppWidget(ids, views);
    }

    public void updateAppWidgetProviderInfo(ComponentName provider, String metaDataKey) {
        if (provider == null) {
            throw new IllegalArgumentException("provider cannot be null");
        }
    }

    public void notifyAppWidgetViewDataChanged(int[] appWidgetIds, int viewId) {
    }

    public void notifyAppWidgetViewDataChanged(int appWidgetId, int viewId) {
    }

    public List<AppWidgetProviderInfo> getInstalledProvidersForProfile(UserHandle profile) {
        return Collections.emptyList();
    }

    public List<AppWidgetProviderInfo> getInstalledProvidersForPackage(
            String packageName, UserHandle profile) {
        if (packageName == null) {
            throw new NullPointerException(
                    "A non-null package must be passed to this method.");
        }
        return Collections.emptyList();
    }

    public List<AppWidgetProviderInfo> getInstalledProviders() {
        return Collections.emptyList();
    }

    public synchronized AppWidgetProviderInfo getAppWidgetInfo(int appWidgetId) {
        AppWidgetProviderInfo info = mWidgetInfos.get(Integer.valueOf(appWidgetId));
        return info != null ? info.clone() : null;
    }

    public boolean bindAppWidgetIdIfAllowed(int appWidgetId, ComponentName provider) {
        return bindAppWidgetIdIfAllowed(appWidgetId, provider, null);
    }

    public boolean bindAppWidgetIdIfAllowed(
            int appWidgetId, ComponentName provider, Bundle options) {
        return bindAppWidgetIdIfAllowed(
                appWidgetId, UserHandle.of(UserHandle.myUserId()), provider, options);
    }

    public synchronized boolean bindAppWidgetIdIfAllowed(
            int appWidgetId, UserHandle user, ComponentName provider, Bundle options) {
        if (user == null) {
            throw new NullPointerException("user");
        }
        if (provider == null) {
            throw new IllegalArgumentException("provider cannot be null");
        }
        return false;
    }

    public synchronized int[] getAppWidgetIds(ComponentName provider) {
        if (provider == null) {
            throw new NullPointerException("provider");
        }
        ArrayList<Integer> list = new ArrayList<Integer>();
        for (Map.Entry<Integer, ComponentName> entry : mBoundProviders.entrySet()) {
            if (provider.equals(entry.getValue())) {
                list.add(entry.getKey());
            }
        }
        int[] ids = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            ids[i] = list.get(i).intValue();
        }
        return ids;
    }

    public boolean isRequestPinAppWidgetSupported() {
        return false;
    }

    public boolean requestPinAppWidget(
            ComponentName provider, Bundle extras, PendingIntent successCallback) {
        if (provider == null) {
            throw new NullPointerException("provider");
        }
        return false;
    }

    private static String previewKey(ComponentName provider, int widgetCategories) {
        return provider.flattenToString() + "#" + widgetCategories;
    }

    public synchronized boolean setWidgetPreview(
            ComponentName provider, int widgetCategories, RemoteViews preview) {
        if (provider == null) {
            throw new NullPointerException("provider");
        }
        mWidgetPreviews.put(previewKey(provider, widgetCategories), preview);
        return true;
    }

    public synchronized RemoteViews getWidgetPreview(
            ComponentName provider, UserHandle profile, int widgetCategory) {
        if (provider == null) {
            throw new NullPointerException("provider");
        }
        return mWidgetPreviews.get(previewKey(provider, widgetCategory));
    }

    public synchronized void removeWidgetPreview(
            ComponentName provider, int widgetCategories) {
        if (provider == null) {
            throw new NullPointerException("provider");
        }
        mWidgetPreviews.remove(previewKey(provider, widgetCategories));
    }

    synchronized RemoteViews getCachedRemoteViews(int appWidgetId) {
        return mWidgetViews.get(Integer.valueOf(appWidgetId));
    }

    synchronized void removeWidgetState(int appWidgetId) {
        Integer key = Integer.valueOf(appWidgetId);
        mBoundProviders.remove(key);
        mWidgetInfos.remove(key);
        mWidgetOptions.remove(key);
        mWidgetViews.remove(key);
    }
}
