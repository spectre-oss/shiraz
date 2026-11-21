// SPDX-License-Identifier: GPL-3.0-only
package android.media.metrics;

import java.util.Objects;

/**
 * Identifier for a media playback/recording/editing metrics session.
 */
public final class LogSessionId {
    public static final LogSessionId LOG_SESSION_ID_NONE = new LogSessionId("");

    private final String mSessionId;

    public LogSessionId(String id) {
        mSessionId = id != null ? id : "";
    }

    public String getStringId() {
        return mSessionId;
    }

    @Override
    public String toString() {
        return mSessionId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LogSessionId)) return false;
        LogSessionId other = (LogSessionId) o;
        return Objects.equals(mSessionId, other.mSessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mSessionId);
    }
}
