// SPDX-License-Identifier: GPL-3.0-only
package android.media.metrics;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class MediaMetricsManager {
    public static final long INVALID_TIMESTAMP = -1L;

    private final Set<String> mActiveSessionIds =
            Collections.synchronizedSet(new HashSet<String>());

    public MediaMetricsManager() {
    }

    private String allocateSessionId(String prefix) {
        String id = prefix + "-" + UUID.randomUUID().toString();
        mActiveSessionIds.add(id);
        return id;
    }

    public PlaybackSession createPlaybackSession() {
        return new PlaybackSession(allocateSessionId("playback"), this);
    }

    public RecordingSession createRecordingSession() {
        return new RecordingSession(allocateSessionId("recording"), this);
    }

    public TranscodingSession createTranscodingSession() {
        return new TranscodingSession(allocateSessionId("transcoding"), this);
    }

    public EditingSession createEditingSession() {
        return new EditingSession(allocateSessionId("editing"), this);
    }

    public BundleSession createBundleSession() {
        return new BundleSession(allocateSessionId("bundle"), this);
    }

    public void releaseSessionId(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        mActiveSessionIds.remove(sessionId);
    }
}
