// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.ondevicepersonalization;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import java.util.Objects;

/** Base service implemented by On-Device Personalization isolated workloads. */
public abstract class IsolatedService extends Service {
    private final IBinder mBinder = new Binder();

    public IsolatedService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public abstract IsolatedWorker onRequest(RequestToken requestToken);

    public final KeyValueStore getRemoteData(RequestToken requestToken) {
        Objects.requireNonNull(requestToken, "requestToken");
        return requestToken.remoteData();
    }

    public final MutableKeyValueStore getLocalData(RequestToken requestToken) {
        Objects.requireNonNull(requestToken, "requestToken");
        return requestToken.localData();
    }

    public final LogReader getLogReader(RequestToken requestToken) {
        Objects.requireNonNull(requestToken, "requestToken");
        return requestToken.logReader();
    }

    public final EventUrlProvider getEventUrlProvider(RequestToken requestToken) {
        Objects.requireNonNull(requestToken, "requestToken");
        return requestToken.eventUrlProvider();
    }

    public final UserData getUserData(RequestToken requestToken) {
        Objects.requireNonNull(requestToken, "requestToken");
        return requestToken.userData();
    }

    public final FederatedComputeScheduler getFederatedComputeScheduler(
            RequestToken requestToken) {
        Objects.requireNonNull(requestToken, "requestToken");
        return requestToken.scheduler();
    }

    public final ModelManager getModelManager(RequestToken requestToken) {
        Objects.requireNonNull(requestToken, "requestToken");
        return requestToken.modelManager();
    }
}
