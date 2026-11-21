// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.measurement;

import android.adservices.common.AdServicesOutcomeReceiver;
import android.content.Context;
import android.net.Uri;
import android.os.OutcomeReceiver;
import android.view.InputEvent;
import java.util.Objects;
import java.util.concurrent.Executor;

/** Attribution Reporting manager; reports MEASUREMENT_API_STATE_DISABLED on the host. */
public class MeasurementManager {
    public static final int MEASUREMENT_API_STATE_DISABLED = 0;
    public static final int MEASUREMENT_API_STATE_ENABLED = 1;

    private final Context mContext;

    MeasurementManager(Context context) {
        this.mContext = context;
    }

    public static MeasurementManager get(Context context) {
        Objects.requireNonNull(context, "context");
        return new MeasurementManager(context);
    }

    public void registerSource(
            Uri attributionSource,
            InputEvent inputEvent,
            Executor executor,
            OutcomeReceiver<Object, Exception> callback) {
        Objects.requireNonNull(attributionSource, "attributionSource");
        failOutcome(executor, callback);
    }

    @SuppressWarnings("deprecation")
    public void registerSource(
            Uri attributionSource,
            InputEvent inputEvent,
            Executor executor,
            AdServicesOutcomeReceiver<Object, Exception> callback) {
        Objects.requireNonNull(attributionSource, "attributionSource");
        failAdServices(executor, callback);
    }

    public void registerSource(
            SourceRegistrationRequest request,
            Executor executor,
            OutcomeReceiver<Object, Exception> callback) {
        Objects.requireNonNull(request, "request");
        failOutcome(executor, callback);
    }

    @SuppressWarnings("deprecation")
    public void registerSource(
            SourceRegistrationRequest request,
            Executor executor,
            AdServicesOutcomeReceiver<Object, Exception> callback) {
        Objects.requireNonNull(request, "request");
        failAdServices(executor, callback);
    }

    public void registerWebSource(
            WebSourceRegistrationRequest request,
            Executor executor,
            OutcomeReceiver<Object, Exception> callback) {
        Objects.requireNonNull(request, "request");
        failOutcome(executor, callback);
    }

    @SuppressWarnings("deprecation")
    public void registerWebSource(
            WebSourceRegistrationRequest request,
            Executor executor,
            AdServicesOutcomeReceiver<Object, Exception> callback) {
        Objects.requireNonNull(request, "request");
        failAdServices(executor, callback);
    }

    public void registerWebTrigger(
            WebTriggerRegistrationRequest request,
            Executor executor,
            OutcomeReceiver<Object, Exception> callback) {
        Objects.requireNonNull(request, "request");
        failOutcome(executor, callback);
    }

    @SuppressWarnings("deprecation")
    public void registerWebTrigger(
            WebTriggerRegistrationRequest request,
            Executor executor,
            AdServicesOutcomeReceiver<Object, Exception> callback) {
        Objects.requireNonNull(request, "request");
        failAdServices(executor, callback);
    }

    public void registerTrigger(
            Uri trigger, Executor executor, OutcomeReceiver<Object, Exception> callback) {
        Objects.requireNonNull(trigger, "trigger");
        failOutcome(executor, callback);
    }

    @SuppressWarnings("deprecation")
    public void registerTrigger(
            Uri trigger,
            Executor executor,
            AdServicesOutcomeReceiver<Object, Exception> callback) {
        Objects.requireNonNull(trigger, "trigger");
        failAdServices(executor, callback);
    }

    public void deleteRegistrations(
            DeletionRequest deletionRequest,
            Executor executor,
            OutcomeReceiver<Object, Exception> callback) {
        Objects.requireNonNull(deletionRequest, "deletionRequest");
        if (executor != null && callback != null) {
            executor.execute(
                    new Runnable() {
                        @Override
                        public void run() {
                            callback.onResult(null);
                        }
                    });
        }
    }

    @SuppressWarnings("deprecation")
    public void deleteRegistrations(
            DeletionRequest deletionRequest,
            Executor executor,
            final AdServicesOutcomeReceiver<Object, Exception> callback) {
        Objects.requireNonNull(deletionRequest, "deletionRequest");
        if (executor != null && callback != null) {
            executor.execute(
                    new Runnable() {
                        @Override
                        public void run() {
                            callback.onResult(null);
                        }
                    });
        }
    }

    public void getMeasurementApiStatus(
            Executor executor, final OutcomeReceiver<Integer, Exception> callback) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(callback, "callback");
        executor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        callback.onResult(Integer.valueOf(MEASUREMENT_API_STATE_DISABLED));
                    }
                });
    }

    @SuppressWarnings("deprecation")
    public void getMeasurementApiStatus(
            Executor executor,
            final AdServicesOutcomeReceiver<Integer, Exception> callback) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(callback, "callback");
        executor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        callback.onResult(Integer.valueOf(MEASUREMENT_API_STATE_DISABLED));
                    }
                });
    }

    private static void failOutcome(
            Executor executor, final OutcomeReceiver<Object, Exception> callback) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(callback, "callback");
        executor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        callback.onError(
                                new IllegalStateException(
                                        "Measurement API is disabled on this host"));
                    }
                });
    }

    private static void failAdServices(
            Executor executor,
            final AdServicesOutcomeReceiver<Object, Exception> callback) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(callback, "callback");
        executor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        callback.onError(
                                new IllegalStateException(
                                        "Measurement API is disabled on this host"));
                    }
                });
    }
}
