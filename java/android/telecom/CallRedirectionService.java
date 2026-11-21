// SPDX-License-Identifier: GPL-3.0-only
package android.telecom;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import java.util.Objects;

/** Service implemented by apps that inspect, redirect, or cancel outgoing calls. */
public abstract class CallRedirectionService extends Service {
    public static final String SERVICE_INTERFACE = "android.telecom.CallRedirectionService";

    private final IBinder mBinder = new Binder();
    private volatile boolean mDecisionMade;
    private volatile boolean mCancelled;
    private volatile Uri mRedirectedHandle;
    private volatile PhoneAccountHandle mRedirectedPhoneAccount;
    private volatile boolean mConfirmFirst;

    public CallRedirectionService() {
    }

    public abstract void onPlaceCall(
            Uri handle, PhoneAccountHandle initialPhoneAccount, boolean allowInteractiveResponse);

    public void onRedirectionTimeout() {
    }

    public final void placeCallUnmodified() {
        ensureNotDecided();
        mDecisionMade = true;
    }

    public final void redirectCall(
            Uri gatewayUri, PhoneAccountHandle targetPhoneAccount, boolean confirmFirst) {
        Objects.requireNonNull(gatewayUri, "gatewayUri");
        Objects.requireNonNull(targetPhoneAccount, "targetPhoneAccount");
        ensureNotDecided();
        mDecisionMade = true;
        mRedirectedHandle = gatewayUri;
        mRedirectedPhoneAccount = targetPhoneAccount;
        mConfirmFirst = confirmFirst;
    }

    public final void cancelCall() {
        ensureNotDecided();
        mDecisionMade = true;
        mCancelled = true;
    }

    @Override
    public final IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public final boolean onUnbind(Intent intent) {
        return false;
    }

    private void ensureNotDecided() {
        if (mDecisionMade) {
            throw new IllegalStateException("Redirection decision has already been made");
        }
    }
}
