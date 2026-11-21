// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.ondevicepersonalization;

import android.os.PersistableBundle;
import java.util.Objects;

/** Input passed to {@link IsolatedWorker#onExecute}. */
public final class ExecuteInput {
    private final String mAppPackageName;
    private final PersistableBundle mAppParams;

    ExecuteInput(String appPackageName, PersistableBundle appParams) {
        this.mAppPackageName = Objects.requireNonNull(appPackageName, "appPackageName");
        this.mAppParams = appParams == null ? PersistableBundle.EMPTY : appParams;
    }

    public String getAppPackageName() {
        return mAppPackageName;
    }

    public PersistableBundle getAppParams() {
        return mAppParams;
    }
}
