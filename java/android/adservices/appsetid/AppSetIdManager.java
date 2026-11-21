// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.appsetid;

import android.content.Context;
import android.os.OutcomeReceiver;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;

/** Manager providing a deterministic app-scoped AppSetId per package on the host. */
public class AppSetIdManager {
    private final Context mContext;

    AppSetIdManager(Context context) {
        this.mContext = context;
    }

    public static AppSetIdManager get(Context context) {
        Objects.requireNonNull(context, "context");
        return new AppSetIdManager(context);
    }

    public void getAppSetId(
            Executor executor, final OutcomeReceiver<AppSetId, Exception> callback) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(callback, "callback");
        String pkg = mContext != null ? mContext.getPackageName() : null;
        if (pkg == null || pkg.isEmpty()) {
            pkg = "shiraz.app";
        }
        final String id =
                UUID.nameUUIDFromBytes(
                                ("shiraz.appsetid:" + pkg).getBytes(StandardCharsets.UTF_8))
                        .toString();
        executor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        callback.onResult(new AppSetId(id, AppSetId.SCOPE_APP));
                    }
                });
    }
}
