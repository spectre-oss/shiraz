// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.content.Context;
import android.telephony.mbms.MbmsErrors;
import android.telephony.mbms.MbmsStreamingSessionCallback;
import android.telephony.mbms.StreamingService;
import android.telephony.mbms.StreamingServiceCallback;
import android.telephony.mbms.StreamingServiceInfo;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

public class MbmsStreamingSession implements AutoCloseable {
    private MbmsStreamingSession() {}

    public static MbmsStreamingSession create(
            Context context,
            Executor executor,
            int subscriptionId,
            final MbmsStreamingSessionCallback callback) {
        Objects.requireNonNull(context, "Context must be non-null");
        Objects.requireNonNull(executor, "Executor must be non-null");
        Objects.requireNonNull(callback, "Callback must be non-null");
        executor.execute(new Runnable() {
            public void run() {
                callback.onError(
                        MbmsErrors.ERROR_NO_UNIQUE_MIDDLEWARE,
                        "No eMBMS streaming middleware available on host");
            }
        });
        return null;
    }

    public static MbmsStreamingSession create(
            Context context, Executor executor, MbmsStreamingSessionCallback callback) {
        return create(context, executor, SubscriptionManager.getDefaultSubscriptionId(), callback);
    }

    public void close() {}

    public void requestUpdateStreamingServices(List<String> serviceClassList) {}

    public StreamingService startStreaming(
            StreamingServiceInfo serviceInfo,
            Executor executor,
            StreamingServiceCallback callback) {
        return null;
    }
}
