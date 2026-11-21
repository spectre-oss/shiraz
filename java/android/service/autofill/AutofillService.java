// SPDX-License-Identifier: GPL-3.0-only
package android.service.autofill;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.CancellationSignal;
import android.os.IBinder;

/** An AutofillService is a service used to automatically fill the contents of the screen on behalf of a given user. */
public abstract class AutofillService extends Service {
    public static final String SERVICE_INTERFACE = "android.service.autofill.AutofillService";
    public static final String SERVICE_META_DATA = "android.autofill";
    public static final String EXTRA_FILL_RESPONSE = "android.service.autofill.extra.FILL_RESPONSE";

    private final IBinder mBinder = new Binder();

    public AutofillService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public final IBinder onBind(Intent intent) {
        if (intent != null && SERVICE_INTERFACE.equals(intent.getAction())) {
            return mBinder;
        }
        return null;
    }

    public void onConnected() {
    }

    public abstract void onFillRequest(
            FillRequest request, CancellationSignal cancellationSignal, FillCallback callback);

    public abstract void onSaveRequest(SaveRequest request, SaveCallback callback);

    public void onSavedDatasetsInfoRequest(SavedDatasetsInfoCallback callback) {
        if (callback != null) {
            callback.onError(SavedDatasetsInfoCallback.ERROR_UNSUPPORTED);
        }
    }

    public void onDisconnected() {
    }

    public final FillEventHistory getFillEventHistory() {
        return null;
    }
}
