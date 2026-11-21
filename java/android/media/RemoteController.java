// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import android.content.Context;
import android.os.Looper;
import android.view.KeyEvent;

@Deprecated
public final class RemoteController {
    public static final int POSITION_SYNCHRONIZATION_NONE = 0;
    public static final int POSITION_SYNCHRONIZATION_CHECK = 1;

    private final OnClientUpdateListener mClientUpdateListener;

    public RemoteController(Context context, OnClientUpdateListener updateListener)
            throws IllegalArgumentException {
        this(context, updateListener, null);
    }

    public RemoteController(
            Context context, OnClientUpdateListener updateListener, Looper looper)
            throws IllegalArgumentException {
        if (context == null) {
            throw new IllegalArgumentException("Invalid null Context");
        }
        if (updateListener == null) {
            throw new IllegalArgumentException("Invalid null OnClientUpdateListener");
        }
        mClientUpdateListener = updateListener;
    }

    public long getEstimatedMediaPosition() {
        return -1L;
    }

    public boolean sendMediaKeyEvent(KeyEvent keyEvent) throws IllegalArgumentException {
        if (keyEvent == null) {
            throw new IllegalArgumentException("Invalid null KeyEvent");
        }
        return false;
    }

    public boolean seekTo(long timeMs) throws IllegalArgumentException {
        if (timeMs < 0) {
            throw new IllegalArgumentException("illegal negative time value");
        }
        return false;
    }

    public boolean setArtworkConfiguration(int width, int height) throws IllegalArgumentException {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Invalid dimensions");
        }
        return true;
    }

    public boolean clearArtworkConfiguration() {
        return true;
    }

    public boolean setSynchronizationMode(int sync) throws IllegalArgumentException {
        if (sync != POSITION_SYNCHRONIZATION_NONE && sync != POSITION_SYNCHRONIZATION_CHECK) {
            throw new IllegalArgumentException("Unknown synchronization mode " + sync);
        }
        return true;
    }

    public MetadataEditor editMetadata() {
        return new MetadataEditor();
    }

    OnClientUpdateListener shirazUpdateListener() {
        return mClientUpdateListener;
    }

    public class MetadataEditor extends MediaMetadataEditor {
        MetadataEditor() {}

        @Override
        public synchronized void apply() {
            mApplied = true;
        }
    }

    public interface OnClientUpdateListener {
        void onClientChange(boolean clearing);

        void onClientPlaybackStateUpdate(int state);

        void onClientPlaybackStateUpdate(
                int state, long stateChangeTimeMs, long currentPosMs, float speed);

        void onClientTransportControlUpdate(int transportControlFlags);

        void onClientMetadataUpdate(MetadataEditor metadataEditor);
    }
}
