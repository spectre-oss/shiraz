// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.signals;

import android.content.Context;
import android.os.OutcomeReceiver;
import java.util.Objects;
import java.util.concurrent.Executor;

/** Manager for Protected App Signals; reports AdServices disabled on the host. */
public class ProtectedSignalsManager {
    private final Context mContext;

    ProtectedSignalsManager(Context context) {
        this.mContext = context;
    }

    public static ProtectedSignalsManager get(Context context) {
        Objects.requireNonNull(context, "context");
        return new ProtectedSignalsManager(context);
    }

    public void updateSignals(
            UpdateSignalsRequest updateSignalsRequest,
            Executor executor,
            final OutcomeReceiver<Object, Exception> receiver) {
        Objects.requireNonNull(updateSignalsRequest, "updateSignalsRequest");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(receiver, "receiver");
        executor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        receiver.onError(
                                new IllegalStateException("AdServices is disabled on this host"));
                    }
                });
    }
}
