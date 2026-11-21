// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.customaudience;

import android.adservices.common.AdServicesOutcomeReceiver;
import android.content.Context;
import android.os.OutcomeReceiver;
import java.util.Objects;
import java.util.concurrent.Executor;

/** Manager for Protected Audience custom audiences; reports AdServices disabled on the host. */
public class CustomAudienceManager {
    private final Context mContext;
    private final TestCustomAudienceManager mTestManager = new TestCustomAudienceManager();

    CustomAudienceManager(Context context) {
        this.mContext = context;
    }

    public static CustomAudienceManager get(Context context) {
        Objects.requireNonNull(context, "context");
        return new CustomAudienceManager(context);
    }

    public TestCustomAudienceManager getTestCustomAudienceManager() {
        return mTestManager;
    }

    public void joinCustomAudience(
            JoinCustomAudienceRequest joinCustomAudienceRequest,
            Executor executor,
            final OutcomeReceiver<Object, Exception> receiver) {
        Objects.requireNonNull(joinCustomAudienceRequest, "joinCustomAudienceRequest");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(receiver, "receiver");
        failDisabled(executor, receiver);
    }

    public void fetchAndJoinCustomAudience(
            FetchAndJoinCustomAudienceRequest fetchAndJoinCustomAudienceRequest,
            Executor executor,
            final OutcomeReceiver<Object, Exception> receiver) {
        Objects.requireNonNull(
                fetchAndJoinCustomAudienceRequest, "fetchAndJoinCustomAudienceRequest");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(receiver, "receiver");
        failDisabled(executor, receiver);
    }

    public void leaveCustomAudience(
            LeaveCustomAudienceRequest leaveCustomAudienceRequest,
            Executor executor,
            final OutcomeReceiver<Object, Exception> receiver) {
        Objects.requireNonNull(leaveCustomAudienceRequest, "leaveCustomAudienceRequest");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(receiver, "receiver");
        failDisabled(executor, receiver);
    }

    @SuppressWarnings("deprecation")
    public void scheduleCustomAudienceUpdate(
            ScheduleCustomAudienceUpdateRequest request,
            Executor executor,
            final AdServicesOutcomeReceiver<Object, Exception> receiver) {
        Objects.requireNonNull(request, "request");
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
