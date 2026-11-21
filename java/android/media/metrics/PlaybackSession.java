// SPDX-License-Identifier: GPL-3.0-only
package android.media.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class PlaybackSession implements AutoCloseable {
    private final LogSessionId mLogSessionId;
    private final MediaMetricsManager mManager;
    private final List<Object> mReportedEvents = new ArrayList<Object>();
    private boolean mClosed;

    PlaybackSession(String id, MediaMetricsManager manager) {
        mLogSessionId = new LogSessionId(id);
        mManager = manager;
    }

    public void reportPlaybackMetrics(PlaybackMetrics metrics) {
        Objects.requireNonNull(metrics, "metrics must not be null");
        synchronized (mReportedEvents) {
            mReportedEvents.add(metrics);
        }
    }

    public void reportPlaybackErrorEvent(PlaybackErrorEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        synchronized (mReportedEvents) {
            mReportedEvents.add(event);
        }
    }

    public void reportNetworkEvent(NetworkEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        synchronized (mReportedEvents) {
            mReportedEvents.add(event);
        }
    }

    public void reportPlaybackStateEvent(PlaybackStateEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        synchronized (mReportedEvents) {
            mReportedEvents.add(event);
        }
    }

    public void reportTrackChangeEvent(TrackChangeEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        synchronized (mReportedEvents) {
            mReportedEvents.add(event);
        }
    }

    public LogSessionId getSessionId() {
        return mLogSessionId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlaybackSession)) return false;
        PlaybackSession other = (PlaybackSession) o;
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
