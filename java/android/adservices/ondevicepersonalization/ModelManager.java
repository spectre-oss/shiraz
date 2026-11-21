// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.ondevicepersonalization;

import android.os.OutcomeReceiver;
import java.util.Objects;
import java.util.concurrent.Executor;

/** Runs ML model inference inside an {@link IsolatedService}. */
public class ModelManager {
    ModelManager() {
    }

    public void run(
            InferenceInput input,
            Executor executor,
            final OutcomeReceiver<InferenceOutput, Exception> receiver) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(receiver, "receiver");
        executor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        receiver.onError(
                                new OnDevicePersonalizationException(
                                        OnDevicePersonalizationException
                                                .ERROR_PERSONALIZATION_DISABLED,
                                        "OnDevicePersonalization model runtime is disabled on this host"));
                    }
                });
    }
}
