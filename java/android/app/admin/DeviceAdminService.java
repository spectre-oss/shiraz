// SPDX-License-Identifier: GPL-3.0-only
package android.app.admin;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

public class DeviceAdminService extends Service {
    private final IBinder mBinder = new Binder();

    public DeviceAdminService() {
    }

    @Override
    public final IBinder onBind(Intent intent) {
        return mBinder;
    }
}
