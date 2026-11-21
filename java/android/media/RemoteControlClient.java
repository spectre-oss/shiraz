// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import android.app.PendingIntent;
import android.graphics.Bitmap;
import android.media.session.MediaSession;
import android.os.Looper;

@Deprecated
public class RemoteControlClient {
    public static final int PLAYSTATE_STOPPED = 1;
    public static final int PLAYSTATE_PAUSED = 2;
    public static final int PLAYSTATE_PLAYING = 3;
    public static final int PLAYSTATE_FAST_FORWARDING = 4;
    public static final int PLAYSTATE_REWINDING = 5;
    public static final int PLAYSTATE_SKIPPING_FORWARDS = 6;
    public static final int PLAYSTATE_SKIPPING_BACKWARDS = 7;
    public static final int PLAYSTATE_BUFFERING = 8;
    public static final int PLAYSTATE_ERROR = 9;

    public static final int FLAG_KEY_MEDIA_PREVIOUS = 1 << 0;
    public static final int FLAG_KEY_MEDIA_REWIND = 1 << 1;
    public static final int FLAG_KEY_MEDIA_PLAY = 1 << 2;
    public static final int FLAG_KEY_MEDIA_PLAY_PAUSE = 1 << 3;
    public static final int FLAG_KEY_MEDIA_PAUSE = 1 << 4;
    public static final int FLAG_KEY_MEDIA_STOP = 1 << 5;
    public static final int FLAG_KEY_MEDIA_FAST_FORWARD = 1 << 6;
    public static final int FLAG_KEY_MEDIA_NEXT = 1 << 7;
    public static final int FLAG_KEY_MEDIA_POSITION_UPDATE = 1 << 8;
    public static final int FLAG_KEY_MEDIA_RATING = 1 << 9;

    private final PendingIntent mRcMediaIntent;
    private int mPlaybackState = PLAYSTATE_STOPPED;
    private long mPlaybackPositionMs = -1L;
    private float mPlaybackSpeed = 1.0f;
    private int mTransportControlFlags;
    private OnMetadataUpdateListener mMetadataUpdateListener;
    private OnPlaybackPositionUpdateListener mPositionUpdateListener;
    private OnGetPlaybackPositionListener mPositionProvider;
    private MetadataEditor mCurrentMetadata;

    public RemoteControlClient(PendingIntent mediaButtonIntent) {
        mRcMediaIntent = mediaButtonIntent;
    }

    public RemoteControlClient(PendingIntent mediaButtonIntent, Looper looper) {
        mRcMediaIntent = mediaButtonIntent;
    }

    public MediaSession getMediaSession() {
        return null;
    }

    public MetadataEditor editMetadata(boolean startEmpty) {
        MetadataEditor editor = new MetadataEditor();
        if (!startEmpty && mCurrentMetadata != null) {
            editor.mEditorArtwork = mCurrentMetadata.mEditorArtwork;
        }
        mCurrentMetadata = editor;
        return editor;
    }

    public void setPlaybackState(int state) {
        setPlaybackState(state, -1L, 1.0f);
    }

    public void setPlaybackState(int state, long timeInMs, float playbackSpeed) {
        mPlaybackState = state;
        mPlaybackPositionMs = timeInMs;
        mPlaybackSpeed = playbackSpeed;
    }

    public void setTransportControlFlags(int transportControlFlags) {
        mTransportControlFlags = transportControlFlags;
    }

    public void setMetadataUpdateListener(OnMetadataUpdateListener l) {
        mMetadataUpdateListener = l;
    }

    public void setPlaybackPositionUpdateListener(OnPlaybackPositionUpdateListener l) {
        mPositionUpdateListener = l;
    }

    public void setOnGetPlaybackPositionListener(OnGetPlaybackPositionListener l) {
        mPositionProvider = l;
    }

    PendingIntent shirazMediaIntent() {
        return mRcMediaIntent;
    }

    int shirazPlaybackState() {
        return mPlaybackState;
    }

    long shirazPlaybackPositionMs() {
        return mPositionProvider != null
                ? mPositionProvider.onGetPlaybackPosition() : mPlaybackPositionMs;
    }

    float shirazPlaybackSpeed() {
        return mPlaybackSpeed;
    }

    int shirazTransportFlags() {
        return mTransportControlFlags;
    }

    OnMetadataUpdateListener shirazMetadataListener() {
        return mMetadataUpdateListener;
    }

    OnPlaybackPositionUpdateListener shirazPositionListener() {
        return mPositionUpdateListener;
    }

    @Deprecated
    public class MetadataEditor extends MediaMetadataEditor {
        public static final int BITMAP_KEY_ARTWORK = 100;

        MetadataEditor() {}

        @Override
        public Object clone() throws CloneNotSupportedException {
            throw new CloneNotSupportedException();
        }

        @Override
        public synchronized MetadataEditor putString(int key, String value)
                throws IllegalArgumentException {
            super.putString(key, value);
            return this;
        }

        @Override
        public synchronized MetadataEditor putLong(int key, long value)
                throws IllegalArgumentException {
            super.putLong(key, value);
            return this;
        }

        @Override
        public synchronized MetadataEditor putBitmap(int key, Bitmap bitmap)
                throws IllegalArgumentException {
            super.putBitmap(key, bitmap);
            return this;
        }

        @Override
        public synchronized MetadataEditor putObject(int key, Object object)
                throws IllegalArgumentException {
            super.putObject(key, object);
            return this;
        }

        @Override
        public synchronized void clear() {
            super.clear();
        }

        @Override
        public synchronized void apply() {
            mApplied = true;
        }
    }

    public interface OnMetadataUpdateListener {
        void onMetadataUpdate(int key, Object newValue);
    }

    public interface OnPlaybackPositionUpdateListener {
        void onPlaybackPositionUpdate(long newPositionMs);
    }

    public interface OnGetPlaybackPositionListener {
        long onGetPlaybackPosition();
    }
}
