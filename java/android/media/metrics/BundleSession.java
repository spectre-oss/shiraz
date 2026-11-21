// SPDX-License-Identifier: GPL-3.0-only
package android.media.metrics;

import android.os.PersistableBundle;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class BundleSession implements AutoCloseable {
    public static final String KEY_STATSD_ATOM = "bundlesession-statsd-atom";

    private final LogSessionId mLogSessionId;
    private final MediaMetricsManager mManager;
    private final List<PersistableBundle> mReportedBundles = new ArrayList<PersistableBundle>();
    private boolean mClosed;

    BundleSession(String id, MediaMetricsManager manager) {
        mLogSessionId = new LogSessionId(id);
        mManager = manager;
    }

    public void reportBundleMetrics(PersistableBundle metrics) {
        Objects.requireNonNull(metrics, "metrics must not be null");
        synchronized (mReportedBundles) {
            mReportedBundles.add(new PersistableBundle(metrics));
        }
    }

    public LogSessionId getSessionId() {
        return mLogSessionId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BundleSession)) return false;
        BundleSession other = (BundleSession) o;
        return Objects.equals(mLogSessionId, other.mLogSessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mLogSessionId);
    }

    @Override
    public void close() {
        if (!mClosed) {
            mClosed = true;
            if (mManager != null) {
                mManager.releaseSessionId(mLogSessionId.getStringId());
            }
        }
    }
}
