// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.customaudience;

import android.os.OutcomeReceiver;
import java.util.Objects;
import java.util.concurrent.Executor;

/** Test manager for custom audience remote overrides. */
public class TestCustomAudienceManager {
    TestCustomAudienceManager() {
    }

    public void overrideCustomAudienceRemoteInfo(
            AddCustomAudienceOverrideRequest request,
            Executor executor,
            final OutcomeReceiver<Object, Exception> receiver) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(receiver, "receiver");
        failDisabled(executor, receiver);
    }

    public void removeCustomAudienceRemoteInfoOverride(
            RemoveCustomAudienceOverrideRequest request,
            Executor executor,
            final OutcomeReceiver<Object, Exception> receiver) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(receiver, "receiver");
        failDisabled(executor, receiver);
    }

    public void resetAllCustomAudienceOverrides(
            Executor executor, final OutcomeReceiver<Object, Exception> receiver) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(receiver, "receiver");
        failDisabled(executor, receiver);
    }

    private static void failDisabled(
            Executor executor, final OutcomeReceiver<Object, Exception> receiver) {
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
