// SPDX-License-Identifier: GPL-3.0-only
package android.appwidget;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.SparseArray;
import android.widget.RemoteViews;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class AppWidgetHost {
    private static final AtomicInteger NEXT_WIDGET_ID = new AtomicInteger(1);
    private static final List<AppWidgetHost> ACTIVE_HOSTS = new ArrayList<AppWidgetHost>();

    private final Context mContext;
    private final int mHostId;
    private final LinkedHashSet<Integer> mAllocatedIds = new LinkedHashSet<Integer>();
    private final SparseArray<AppWidgetHostView> mViews = new SparseArray<AppWidgetHostView>();
    private boolean mListening;

    public AppWidgetHost(Context context, int hostId) {
        if (context == null) {
            throw new NullPointerException("context");
        }
        mContext = context;
        mHostId = hostId;
        synchronized (ACTIVE_HOSTS) {
            ACTIVE_HOSTS.add(this);
        }
    }

    static void dispatchRemoteViewsUpdate(int appWidgetId, RemoteViews views) {
        List<AppWidgetHost> snapshot;
        synchronized (ACTIVE_HOSTS) {
            snapshot = new ArrayList<AppWidgetHost>(ACTIVE_HOSTS);
        }
        for (int i = 0; i < snapshot.size(); i++) {
            snapshot.get(i).deliverUpdateIfListening(appWidgetId, views);
        }
    }

    private void deliverUpdateIfListening(int appWidgetId, RemoteViews views) {
        AppWidgetHostView view = null;
        synchronized (this) {
            if (mListening) {
                view = mViews.get(appWidgetId);
            }
        }
        if (view != null) {
            view.updateAppWidget(views);
        }
    }

    public void startListening() {
        ArrayList<AppWidgetHostView> viewsToSync = new ArrayList<AppWidgetHostView>();
        ArrayList<Integer> idsToSync = new ArrayList<Integer>();
        synchronized (this) {
            mListening = true;
            for (int i = 0; i < mViews.size(); i++) {
                idsToSync.add(Integer.valueOf(mViews.keyAt(i)));
                viewsToSync.add(mViews.valueAt(i));
            }
        }
        AppWidgetManager mgr = AppWidgetManager.getInstance(mContext);
        for (int i = 0; i < viewsToSync.size(); i++) {
            RemoteViews rv = mgr.getCachedRemoteViews(idsToSync.get(i).intValue());
            if (rv != null) {
                viewsToSync.get(i).updateAppWidget(rv);
            }
        }
    }

    public void stopListening() {
        synchronized (this) {
            mListening = false;
        }
    }

    public synchronized int allocateAppWidgetId() {
        int id = NEXT_WIDGET_ID.getAndIncrement();
        mAllocatedIds.add(Integer.valueOf(id));
        return id;
    }

    public final void startAppWidgetConfigureActivityForResult(
            Activity activity,
            int appWidgetId,
            int intentFlags,
            int requestCode,
            Bundle options) {
        if (activity == null) {
            throw new NullPointerException("activity");
        }
        AppWidgetProviderInfo info =
                AppWidgetManager.getInstance(mContext).getAppWidgetInfo(appWidgetId);
        if (info == null || info.configure == null) {
            throw new ActivityNotFoundException(
                    "No configure activity bound for appWidgetId " + appWidgetId);
        }
        Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
        intent.setComponent(info.configure);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        intent.addFlags(intentFlags);
        activity.startActivityForResult(intent, requestCode, options);
    }

    public synchronized int[] getAppWidgetIds() {
        int[] result = new int[mAllocatedIds.size()];
        int idx = 0;
        for (Integer id : mAllocatedIds) {
            result[idx++] = id.intValue();
        }
        return result;
    }

    public void deleteAppWidgetId(int appWidgetId) {
        synchronized (this) {
            mAllocatedIds.remove(Integer.valueOf(appWidgetId));
            mViews.remove(appWidgetId);
        }
        AppWidgetManager.getInstance(mContext).removeWidgetState(appWidgetId);
    }

    public void deleteHost() {
        int[] ids;
        synchronized (this) {
            ids = getAppWidgetIds();
            mAllocatedIds.clear();
            mViews.clear();
            mListening = false;
        }
        AppWidgetManager mgr = AppWidgetManager.getInstance(mContext);
        for (int i = 0; i < ids.length; i++) {
            mgr.removeWidgetState(ids[i]);
        }
    }

    public static void deleteAllHosts() {
        List<AppWidgetHost> snapshot;
        synchronized (ACTIVE_HOSTS) {
            snapshot = new ArrayList<AppWidgetHost>(ACTIVE_HOSTS);
        }
        for (int i = 0; i < snapshot.size(); i++) {
            snapshot.get(i).deleteHost();
        }
    }

    public final AppWidgetHostView createView(
            Context context, int appWidgetId, AppWidgetProviderInfo appWidget) {
        AppWidgetHostView view = onCreateView(context, appWidgetId, appWidget);
        view.setAppWidget(appWidgetId, appWidget);
        synchronized (this) {
            mAllocatedIds.add(Integer.valueOf(appWidgetId));
            mViews.put(appWidgetId, view);
        }
        RemoteViews cached =
                AppWidgetManager.getInstance(mContext).getCachedRemoteViews(appWidgetId);
        view.updateAppWidget(cached);
        return view;
    }

    protected AppWidgetHostView onCreateView(
            Context context, int appWidgetId, AppWidgetProviderInfo appWidget) {
        return new AppWidgetHostView(context);
    }

    protected void onProviderChanged(int appWidgetId, AppWidgetProviderInfo appWidget) {
        AppWidgetHostView v;
        synchronized (this) {
            v = mViews.get(appWidgetId);
        }
        if (v != null) {
            v.setAppWidget(appWidgetId, appWidget);
        }
    }

    public void onAppWidgetRemoved(int appWidgetId) {
        deleteAppWidgetId(appWidgetId);
    }

    protected void onProvidersChanged() {
    }

    protected void clearViews() {
        synchronized (this) {
            mViews.clear();
        }
    }
}
