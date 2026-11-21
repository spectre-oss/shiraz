// SPDX-License-Identifier: GPL-3.0-only

package android.view.contentcapture;

import android.content.ComponentName;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;

public final class ContentCaptureManager {
    public static final int DATA_SHARE_ERROR_UNKNOWN = 1;
    public static final int DATA_SHARE_ERROR_CONCURRENT_REQUEST = 2;
    public static final int DATA_SHARE_ERROR_TIMEOUT_INTERRUPTED = 3;

    private boolean mEnabled;

    ContentCaptureManager() {
    }

    public ComponentName getServiceComponentName() {
        return null;
    }

    public boolean isContentCaptureEnabled() {
        return mEnabled;
    }

    public Set<ContentCaptureCondition> getContentCaptureConditions() {
        return Collections.emptySet();
    }

    public void setContentCaptureEnabled(boolean enabled) {
        mEnabled = enabled;
    }

    public void removeData(DataRemovalRequest request) {
        Objects.requireNonNull(request, "request");
    }

    public void shareData(
            DataShareRequest request,
            Executor executor,
            final DataShareWriteAdapter dataShareWriteAdapter) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(dataShareWriteAdapter, "dataShareWriteAdapter");
        executor.execute(new Runnable() {
            @Override
            public void run() {
                dataShareWriteAdapter.onError(DATA_SHARE_ERROR_UNKNOWN);
            }
        });
    }
}
