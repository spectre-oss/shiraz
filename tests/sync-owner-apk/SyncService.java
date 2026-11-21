// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.syncownerfixture;
import android.app.Service;
import android.content.AbstractThreadedSyncAdapter;
import android.content.Intent;
import android.os.IBinder;

/** Parent connection loss must stop the worker without app cleanup hooks. */
public final class SyncService extends Service {
    private AbstractThreadedSyncAdapter adapter;
    public void onCreate(){super.onCreate();adapter=SyncActivity.start(getApplicationContext(),null);}
    public IBinder onBind(Intent intent){return adapter.getSyncAdapterBinder();}
    public void onDestroy(){super.onDestroy();System.out.println("SYNC_APK_SERVICE_DESTROYED");}
}
