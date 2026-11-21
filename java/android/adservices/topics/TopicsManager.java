// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.topics;

import android.content.Context;
import android.os.OutcomeReceiver;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.Executor;

/** Topics API manager; returns an empty topic list or disabled state on the host. */
public final class TopicsManager {
    private final Context mContext;

    TopicsManager(Context context) {
        this.mContext = context;
    }

    public static TopicsManager get(Context context) {
        Objects.requireNonNull(context, "context");
        return new TopicsManager(context);
    }

    public void getTopics(
            GetTopicsRequest getTopicsRequest,
            Executor executor,
            final OutcomeReceiver<GetTopicsResponse, Exception> callback) {
        Objects.requireNonNull(getTopicsRequest, "getTopicsRequest");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(callback, "callback");
        executor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        callback.onResult(
                                new GetTopicsResponse.Builder(
                                                Collections.<Topic>emptyList(),
                                                Collections.<EncryptedTopic>emptyList())
                                        .build());
                    }
                });
    }
}
