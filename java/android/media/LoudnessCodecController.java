// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import android.os.Bundle;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

public class LoudnessCodecController implements AutoCloseable {
    private final int mSessionId;
    private final Executor mExecutor;
    private final OnLoudnessCodecUpdateListener mListener;
    private final Set<MediaCodec> mMediaCodecs =
            Collections.newSetFromMap(new ConcurrentHashMap<MediaCodec, Boolean>());
    private volatile boolean mClosed;

    private LoudnessCodecController(
            int sessionId, Executor executor, OnLoudnessCodecUpdateListener listener) {
        mSessionId = sessionId;
        mExecutor = executor;
        mListener = listener;
    }

    public interface OnLoudnessCodecUpdateListener {
        default Bundle onLoudnessCodecUpdate(MediaCodec mediaCodec, Bundle codecValues) {
            return codecValues;
        }
    }

    public static LoudnessCodecController create(int sessionId) {
        return new LoudnessCodecController(sessionId, null, null);
    }

    public static LoudnessCodecController create(
            int sessionId, Executor executor, OnLoudnessCodecUpdateListener listener) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(listener, "listener");
        return new LoudnessCodecController(sessionId, executor, listener);
    }

    public boolean addMediaCodec(MediaCodec mediaCodec) {
        Objects.requireNonNull(mediaCodec, "mediaCodec");
        if (mClosed) {
            throw new IllegalStateException("LoudnessCodecController is closed");
        }
        if (!mMediaCodecs.add(mediaCodec)) {
            throw new IllegalArgumentException("MediaCodec was already added");
        }
        if (mListener != null && mExecutor != null) {
            final MediaCodec target = mediaCodec;
            mExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    Bundle params = mListener.onLoudnessCodecUpdate(target, new Bundle());
                    if (params != null && !params.isEmpty()) {
                        try {
                            target.setParameters(params);
                        } catch (RuntimeException ignored) {
                        }
                    }
                }
            });
        }
        return true;
    }

    public void removeMediaCodec(MediaCodec mediaCodec) {
        Objects.requireNonNull(mediaCodec, "mediaCodec");
        if (!mMediaCodecs.remove(mediaCodec)) {
            throw new IllegalArgumentException("MediaCodec was not registered");
        }
    }

    public Bundle getLoudnessCodecParams(MediaCodec mediaCodec) {
        Objects.requireNonNull(mediaCodec, "mediaCodec");
        return new Bundle();
    }

    public int shirazSessionId() {
        return mSessionId;
    }

    @Override
    public void close() {
        mClosed = true;
        mMediaCodecs.clear();
    }
}
