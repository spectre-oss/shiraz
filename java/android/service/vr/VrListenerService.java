// SPDX-License-Identifier: GPL-3.0-only
package android.service.vr;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

public abstract class VrListenerService extends Service {
    public static final String SERVICE_INTERFACE = "android.service.vr.VrListenerService";

    private final IBinder mBinder = new Binder();

    public VrListenerService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public void onCurrentVrActivityChanged(ComponentName component) {
    }

    public static final boolean isVrModePackageEnabled(Context context,
            ComponentName requestedComponent) {
        return false;
    }
}
