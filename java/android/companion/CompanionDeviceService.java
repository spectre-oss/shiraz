// SPDX-License-Identifier: GPL-3.0-only
package android.companion;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import java.io.InputStream;
import java.io.OutputStream;

public abstract class CompanionDeviceService extends Service {
    public static final String SERVICE_INTERFACE = "android.companion.CompanionDeviceService";

    public CompanionDeviceService() {
    }

    @Deprecated
    public void onDeviceAppeared(String address) {
    }

    @Deprecated
    public void onDeviceDisappeared(String address) {
    }

    public final void attachSystemDataTransport(int associationId, InputStream in, OutputStream out)
            throws DeviceNotAssociatedException {
        throw new DeviceNotAssociatedException("Association not found: " + associationId);
    }

    public final void detachSystemDataTransport(int associationId)
            throws DeviceNotAssociatedException {
        throw new DeviceNotAssociatedException("Association not found: " + associationId);
    }

    public void onDeviceAppeared(AssociationInfo associationInfo) {
        if (associationInfo != null && !associationInfo.isSelfManaged()
                && associationInfo.getDeviceMacAddress() != null) {
            onDeviceAppeared(associationInfo.getDeviceMacAddress().toString());
        }
    }

    public void onDeviceDisappeared(AssociationInfo associationInfo) {
        if (associationInfo != null && !associationInfo.isSelfManaged()
                && associationInfo.getDeviceMacAddress() != null) {
            onDeviceDisappeared(associationInfo.getDeviceMacAddress().toString());
        }
    }

    @Override
    public final IBinder onBind(Intent intent) {
        if (intent != null && SERVICE_INTERFACE.equals(intent.getAction())) {
            return new Binder();
        }
        return null;
    }
}
