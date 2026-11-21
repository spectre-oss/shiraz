// SPDX-License-Identifier: GPL-3.0-only
package android.os;

import java.util.Objects;
import java.util.concurrent.Executor;

/** System diagnostic bugreport manager. */
public final class BugreportManager {
    public abstract static class BugreportCallback {
        public static final int BUGREPORT_ERROR_INVALID_INPUT = 1;
        public static final int BUGREPORT_ERROR_RUNTIME = 2;
        public static final int BUGREPORT_ERROR_USER_DENIED_CONSENT = 3;
        public static final int BUGREPORT_ERROR_USER_CONSENT_TIMED_OUT = 4;
        public static final int BUGREPORT_ERROR_ANOTHER_REPORT_IN_PROGRESS = 5;
        public static final int BUGREPORT_ERROR_NO_BUGREPORT_TO_RETRIEVE = 6;

        public BugreportCallback() {
        }

        public void onProgress(float progress) {
        }

        public void onError(int errorCode) {
        }

        public void onFinished() {
        }

        public void onEarlyReportFinished() {
        }
    }

    public BugreportManager() {
    }

    public void startConnectivityBugreport(
            ParcelFileDescriptor bugreportFd,
            Executor executor,
            final BugreportCallback callback) {
        Objects.requireNonNull(bugreportFd, "bugreportFd");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(callback, "callback");
        FileUtils.closeQuietly(bugreportFd);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                callback.onError(BugreportCallback.BUGREPORT_ERROR_RUNTIME);
            }
        });
    }

    public void cancelBugreport() {
    }
}
