// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.adid;

import android.adservices.common.AdServicesOutcomeReceiver;
import android.content.Context;
import android.os.OutcomeReceiver;
import java.util.Objects;
import java.util.concurrent.Executor;

/** Manager for retrieving the Advertising ID; returns zeroed-out opted-out AdId on the host. */
public class AdIdManager {
    private final Context mContext;

    AdIdManager(Context context) {
        this.mContext = context;
    }

    public static AdIdManager get(Context context) {
        Objects.requireNonNull(context, "context");
        return new AdIdManager(context);
    }

    public void getAdId(
            Executor executor, final OutcomeReceiver<AdId, Exception> callback) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(callback, "callback");
        executor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        callback.onResult(new AdId(AdId.ZERO_OUT, true));
                    }
                });
    }

    @SuppressWarnings("deprecation")
    public void getAdId(
            Executor executor, final AdServicesOutcomeReceiver<AdId, Exception> callback) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(callback, "callback");
        executor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        callback.onResult(new AdId(AdId.ZERO_OUT, true));
                    }
                });
    }
}
