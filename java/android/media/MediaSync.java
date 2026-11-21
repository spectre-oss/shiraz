// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import java.nio.ByteBuffer;

public final class MediaSync {
    public static final int MEDIASYNC_ERROR_AUDIOTRACK_FAIL = 1;
    public static final int MEDIASYNC_ERROR_SURFACE_FAIL = 2;

    public abstract static class Callback {
        public Callback() {
        }

        public abstract void onAudioBufferConsumed(MediaSync sync, ByteBuffer audioBuffer, int bufferId);
    }

    public interface OnErrorListener {
        void onError(MediaSync sync, int what, int extra);
    }

    private Callback mCallback;
    private Handler mCallbackHandler;
    private OnErrorListener mOnErrorListener;
    private Handler mOnErrorHandler;
    private Surface mSurface;
    private AudioTrack mAudioTrack;
    private PlaybackParams mPlaybackParams = new PlaybackParams().setSpeed(1.0f).setPitch(1.0f);
    private SyncParams mSyncParams = new SyncParams().allowDefaults();
    private long mLastAnchorMediaTimeUs = -1L;
    private long mLastAnchorSystemNanoTime;
    private boolean mReleased;

    public MediaSync() {
    }

    @Override
    protected void finalize() {
        release();
    }

    private void checkNotReleased() {
        if (mReleased) {
            throw new IllegalStateException("MediaSync has been released");
        }
    }

    public synchronized void release() {
        mReleased = true;
        mCallback = null;
        mCallbackHandler = null;
        mOnErrorListener = null;
        mOnErrorHandler = null;
        mSurface = null;
        mAudioTrack = null;
    }

    public synchronized void setCallback(Callback cb, Handler handler) {
        checkNotReleased();
        mCallback = cb;
        if (cb == null) {
            mCallbackHandler = null;
        } else if (handler != null) {
            mCallbackHandler = handler;
        } else {
            Looper looper = Looper.myLooper() != null ? Looper.myLooper() : Looper.getMainLooper();
            mCallbackHandler = looper != null ? new Handler(looper) : null;
        }
    }

    public synchronized void setOnErrorListener(OnErrorListener listener, Handler handler) {
        checkNotReleased();
        mOnErrorListener = listener;
        if (listener == null) {
            mOnErrorHandler = null;
        } else if (handler != null) {
            mOnErrorHandler = handler;
        } else {
            Looper looper = Looper.myLooper() != null ? Looper.myLooper() : Looper.getMainLooper();
            mOnErrorHandler = looper != null ? new Handler(looper) : null;
        }
    }

    public synchronized void setSurface(Surface surface) {
        checkNotReleased();
        if (surface != null && !surface.isValid()) {
            throw new IllegalArgumentException("Surface is not valid");
        }
        mSurface = surface;
    }

    public synchronized void setAudioTrack(AudioTrack audioTrack) {
        checkNotReleased();
        mAudioTrack = audioTrack;
    }

    public synchronized Surface createInputSurface() {
        checkNotReleased();
        if (mSurface == null) {
            throw new IllegalStateException("Output surface is not set");
        }
        return new Surface();
    }

    public synchronized void setPlaybackParams(PlaybackParams params) {
        checkNotReleased();
        if (params == null) {
            throw new IllegalArgumentException("params must not be null");
        }
        mPlaybackParams = params;
    }

    public synchronized PlaybackParams getPlaybackParams() {
        checkNotReleased();
        return mPlaybackParams;
    }

    public synchronized void setSyncParams(SyncParams params) {
        checkNotReleased();
        if (params == null) {
            throw new IllegalArgumentException("params must not be null");
        }
        mSyncParams = params;
    }

    public synchronized SyncParams getSyncParams() {
        checkNotReleased();
        return mSyncParams;
    }

    public synchronized void flush() {
        checkNotReleased();
        if (mAudioTrack != null) {
            try {
                mAudioTrack.flush();
            } catch (RuntimeException ignored) {
            }
        }
        mLastAnchorMediaTimeUs = -1L;
    }

    public synchronized MediaTimestamp getTimestamp() {
        checkNotReleased();
        if (mLastAnchorMediaTimeUs < 0L) {
            return null;
        }
        float speed = 1.0f;
        try {
            speed = mPlaybackParams.getSpeed();
        } catch (RuntimeException ignored) {
        }
        return new MediaTimestamp(mLastAnchorMediaTimeUs, mLastAnchorSystemNanoTime, speed);
    }

    public void queueAudio(final ByteBuffer audioData, final int bufferId, long presentationTimeUs) {
        if (audioData == null || bufferId < 0) {
            throw new IllegalArgumentException("Invalid audioData or bufferId");
        }
        final Callback cb;
        final Handler cbHandler;
        final AudioTrack track;
        synchronized (this) {
            checkNotReleased();
            if (mAudioTrack == null) {
                throw new IllegalStateException("AudioTrack is not configured");
            }
            track = mAudioTrack;
            cb = mCallback;
            cbHandler = mCallbackHandler;
            mLastAnchorMediaTimeUs = presentationTimeUs;
            mLastAnchorSystemNanoTime = System.nanoTime();
        }
        int remaining = audioData.remaining();
        if (remaining > 0) {
            byte[] temp = new byte[remaining];
            audioData.get(temp);
            track.write(temp, 0, temp.length);
        }
        if (cb != null) {
            if (cbHandler != null) {
                cbHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        cb.onAudioBufferConsumed(MediaSync.this, audioData, bufferId);
                    }
                });
            } else {
                cb.onAudioBufferConsumed(this, audioData, bufferId);
            }
        }
    }
}
