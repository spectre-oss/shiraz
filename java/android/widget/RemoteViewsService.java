// SPDX-License-Identifier: GPL-3.0-only
package android.widget;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import java.util.HashMap;

/** Service to be connected to for a remote adapter to request RemoteViews. */
public abstract class RemoteViewsService extends Service {
    public interface RemoteViewsFactory {
        void onCreate();

        void onDataSetChanged();

        void onDestroy();

        int getCount();

        RemoteViews getViewAt(int position);

        RemoteViews getLoadingView();

        int getViewTypeCount();

        long getItemId(int position);

        boolean hasStableIds();
    }

    private final HashMap<Intent.FilterComparison, RemoteViewsFactory> mFactories =
            new HashMap<Intent.FilterComparison, RemoteViewsFactory>();

    public RemoteViewsService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        synchronized (mFactories) {
            Intent.FilterComparison fc = new Intent.FilterComparison(intent);
            RemoteViewsFactory factory = mFactories.get(fc);
            if (factory == null) {
                factory = onGetViewFactory(intent);
                if (factory != null) {
                    factory.onCreate();
                    mFactories.put(fc, factory);
                }
            }
            final RemoteViewsFactory boundFactory = factory;
            return new Binder() {
                RemoteViewsFactory getFactory() {
                    return boundFactory;
                }
            };
        }
    }

    public abstract RemoteViewsFactory onGetViewFactory(Intent intent);
}
