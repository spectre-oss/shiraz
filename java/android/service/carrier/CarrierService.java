// SPDX-License-Identifier: GPL-3.0-only
package android.service.carrier;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.PersistableBundle;

public abstract class CarrierService extends Service {
    public static final String CARRIER_SERVICE_INTERFACE =
            "android.service.carrier.CarrierService";

    private final IBinder mBinder = new Binder();

    public CarrierService() {
    }

    public abstract PersistableBundle onLoadConfig(CarrierIdentifier id);

    public PersistableBundle onLoadConfig(int subId, CarrierIdentifier id) {
        return onLoadConfig(id);
    }

    public final void notifyCarrierNetworkChange(boolean active) {
    }

    public final void notifyCarrierNetworkChange(int subId, boolean active) {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
}
