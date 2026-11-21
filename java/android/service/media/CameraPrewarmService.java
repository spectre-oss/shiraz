// SPDX-License-Identifier: GPL-3.0-only
package android.service.media;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

public abstract class CameraPrewarmService extends Service {
    private final IBinder mBinder = new Binder();

    public CameraPrewarmService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        onPrewarm();
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        onCooldown(false);
        return false;
    }

    public abstract void onPrewarm();

    public abstract void onCooldown(boolean cameraIntentFired);
}
