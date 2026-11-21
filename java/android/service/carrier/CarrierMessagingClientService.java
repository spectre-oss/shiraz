// SPDX-License-Identifier: GPL-3.0-only
package android.service.carrier;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

public class CarrierMessagingClientService extends Service {
    private final IBinder mBinder = new Binder();

    public CarrierMessagingClientService() {
    }

    @Override
    public final IBinder onBind(Intent intent) {
        return mBinder;
    }
}
