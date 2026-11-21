// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.content.Context;
import android.telephony.mbms.GroupCall;
import android.telephony.mbms.GroupCallCallback;
import android.telephony.mbms.MbmsErrors;
import android.telephony.mbms.MbmsGroupCallSessionCallback;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

public class MbmsGroupCallSession implements AutoCloseable {
    private MbmsGroupCallSession() {}

    public static MbmsGroupCallSession create(
            Context context,
            int subscriptionId,
            Executor executor,
            final MbmsGroupCallSessionCallback callback) {
        Objects.requireNonNull(context, "Context must be non-null");
        Objects.requireNonNull(executor, "Executor must be non-null");
        Objects.requireNonNull(callback, "Callback must be non-null");
        executor.execute(new Runnable() {
            public void run() {
                callback.onError(
                        MbmsErrors.ERROR_NO_UNIQUE_MIDDLEWARE,
                        "No eMBMS group-call middleware available on host");
            }
        });
        return null;
    }

    public static MbmsGroupCallSession create(
            Context context, Executor executor, MbmsGroupCallSessionCallback callback) {
        return create(context, SubscriptionManager.getDefaultSubscriptionId(), executor, callback);
    }

    public void close() {}

    public GroupCall startGroupCall(
            long tmgi,
            List<Integer> saiList,
            List<Integer> frequencyList,
            Executor executor,
            GroupCallCallback callback) {
        return null;
    }
}
