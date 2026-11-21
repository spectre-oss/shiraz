// SPDX-License-Identifier: GPL-3.0-only
package android.service.quicksettings;

import android.app.Dialog;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import java.util.Objects;

public class TileService extends Service {
    public static final String ACTION_QS_TILE = "android.service.quicksettings.action.QS_TILE";
    public static final String ACTION_QS_TILE_PREFERENCES =
            "android.service.quicksettings.action.QS_TILE_PREFERENCES";
    public static final String META_DATA_ACTIVE_TILE =
            "android.service.quicksettings.ACTIVE_TILE";
    public static final String META_DATA_TOGGLEABLE_TILE =
            "android.service.quicksettings.TOGGLEABLE_TILE";

    private final IBinder mBinder = new Binder();
    private Tile mTile;

    public TileService() {
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    public void onTileAdded() {
    }

    public void onTileRemoved() {
    }

    public void onStartListening() {
    }

    public void onStopListening() {
    }

    public void onClick() {
    }

    public final void showDialog(Dialog dialog) {
        if (dialog != null) {
            dialog.show();
        }
    }

    public final void unlockAndRun(Runnable runnable) {
        if (runnable != null) {
            runnable.run();
        }
    }

    public final boolean isSecure() {
        return false;
    }

    public final boolean isLocked() {
        return false;
    }

    public final void startActivityAndCollapse(Intent intent) {
        Objects.requireNonNull(intent, "intent must not be null");
        startActivity(intent);
    }

    public final void startActivityAndCollapse(PendingIntent pendingIntent) {
        Objects.requireNonNull(pendingIntent, "pendingIntent must not be null");
        try {
            pendingIntent.send();
        } catch (PendingIntent.CanceledException ignored) {
        }
    }

    public final Tile getQsTile() {
        if (mTile == null) {
            mTile = new Tile();
        }
        return mTile;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public static final void requestListeningState(Context context, ComponentName component) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(component, "component must not be null");
    }
}
