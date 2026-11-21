// SPDX-License-Identifier: GPL-3.0-only
package android.media.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class EditingSession implements AutoCloseable {
    private final LogSessionId mLogSessionId;
    private final MediaMetricsManager mManager;
    private final List<EditingEndedEvent> mEvents = new ArrayList<EditingEndedEvent>();
    private boolean mClosed;

    EditingSession(String id, MediaMetricsManager manager) {
        mLogSessionId = new LogSessionId(id);
        mManager = manager;
    }

    public void reportEditingEndedEvent(EditingEndedEvent editingEndedEvent) {
        Objects.requireNonNull(editingEndedEvent, "editingEndedEvent must not be null");
        synchronized (mEvents) {
            mEvents.add(editingEndedEvent);
        }
    }

    public LogSessionId getSessionId() {
        return mLogSessionId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EditingSession)) return false;
        EditingSession other = (EditingSession) o;
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
