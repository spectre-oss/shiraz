// SPDX-License-Identifier: GPL-3.0-only
package android.service.credentials;

import android.app.Service;
import android.content.Intent;
import android.credentials.ClearCredentialStateException;
import android.credentials.CreateCredentialException;
import android.credentials.GetCredentialException;
import android.os.Binder;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.OutcomeReceiver;

/** Base service implemented by credential providers to supply and store credentials. */
public abstract class CredentialProviderService extends Service {
    public static final String SERVICE_INTERFACE =
            "android.service.credentials.CredentialProviderService";
    public static final String SERVICE_META_DATA = "android.credentials.provider";

    public static final String EXTRA_GET_CREDENTIAL_REQUEST =
            "android.service.credentials.extra.GET_CREDENTIAL_REQUEST";
    public static final String EXTRA_GET_CREDENTIAL_RESPONSE =
            "android.service.credentials.extra.GET_CREDENTIAL_RESPONSE";
    public static final String EXTRA_GET_CREDENTIAL_EXCEPTION =
            "android.service.credentials.extra.GET_CREDENTIAL_EXCEPTION";

    public static final String EXTRA_CREATE_CREDENTIAL_REQUEST =
            "android.service.credentials.extra.CREATE_CREDENTIAL_REQUEST";
    public static final String EXTRA_CREATE_CREDENTIAL_RESPONSE =
            "android.service.credentials.extra.CREATE_CREDENTIAL_RESPONSE";
    public static final String EXTRA_CREATE_CREDENTIAL_EXCEPTION =
            "android.service.credentials.extra.CREATE_CREDENTIAL_EXCEPTION";

    public static final String EXTRA_BEGIN_GET_CREDENTIAL_REQUEST =
            "android.service.credentials.extra.BEGIN_GET_CREDENTIAL_REQUEST";
    public static final String EXTRA_BEGIN_GET_CREDENTIAL_RESPONSE =
            "android.service.credentials.extra.BEGIN_GET_CREDENTIAL_RESPONSE";

    private Handler mHandler;
    private IBinder mInterface;

    public CredentialProviderService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Looper looper = Looper.myLooper();
        if (looper == null) {
            looper = Looper.getMainLooper();
        }
        if (looper != null) {
            mHandler = new Handler(looper);
        }
        mInterface = new Binder();
    }

    @Override
    public final IBinder onBind(Intent intent) {
        if (intent != null && SERVICE_INTERFACE.equals(intent.getAction())) {
            if (mInterface == null) {
                mInterface = new Binder();
            }
            return mInterface;
        }
        return null;
    }

    public abstract void onBeginGetCredential(
            BeginGetCredentialRequest request,
            CancellationSignal cancellationSignal,
            OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException> callback);

    public abstract void onBeginCreateCredential(
            BeginCreateCredentialRequest request,
            CancellationSignal cancellationSignal,
            OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException> callback);

    public abstract void onClearCredentialState(
            ClearCredentialStateRequest request,
            CancellationSignal cancellationSignal,
            OutcomeReceiver<Void, ClearCredentialStateException> callback);
}
