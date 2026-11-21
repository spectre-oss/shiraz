// SPDX-License-Identifier: GPL-3.0-only
package android.webkit;

import java.util.Collections;
import java.util.Map;

/** Web storage is not backed by a browser engine; every operation is stateful or a safe no-op. */
public class WebStorage {
    private static final WebStorage INSTANCE = new WebStorage();

    @Deprecated
    public interface QuotaUpdater {
        void updateQuota(long newQuota);
    }

    public static class Origin {
        private final String mOrigin;
        private final long mQuota;
        private final long mUsage;

        protected Origin(String origin, long quota, long usage) {
            mOrigin = origin;
            mQuota = quota;
            mUsage = usage;
        }

        public String getOrigin() {
            return mOrigin;
        }

        public long getQuota() {
            return mQuota;
        }

        public long getUsage() {
            return mUsage;
        }
    }

    public WebStorage() {
    }

    public static WebStorage getInstance() {
        return INSTANCE;
    }

    public void getOrigins(ValueCallback<Map> callback) {
        if (callback != null) {
            callback.onReceiveValue(Collections.emptyMap());
        }
    }

    public void getUsageForOrigin(String origin, ValueCallback<Long> callback) {
        if (callback != null) {
            callback.onReceiveValue(0L);
        }
    }

    public void getQuotaForOrigin(String origin, ValueCallback<Long> callback) {
        if (callback != null) {
            callback.onReceiveValue(0L);
        }
    }

    @Deprecated
    public void setQuotaForOrigin(String origin, long quota) {
    }

    public void deleteOrigin(String origin) {
    }

    public void deleteAllData() {
    }
}
