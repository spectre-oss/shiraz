// SPDX-License-Identifier: GPL-3.0-only
package android.media.metrics;

import java.util.Objects;

public final class RecordingSession implements AutoCloseable {
    private final LogSessionId mLogSessionId;
    private final MediaMetricsManager mManager;
    private boolean mClosed;

    RecordingSession(String id, MediaMetricsManager manager) {
        mLogSessionId = new LogSessionId(id);
        mManager = manager;
    }

    public LogSessionId getSessionId() {
        return mLogSessionId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RecordingSession)) return false;
        RecordingSession other = (RecordingSession) o;
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
