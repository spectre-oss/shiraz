// SPDX-License-Identifier: GPL-3.0-only
package android.media.tv;

import android.content.Context;
import android.media.tv.interactive.TvInteractiveAppView;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import java.util.Objects;

public class TvRecordingClient {
    public abstract static class RecordingCallback {
        public RecordingCallback() {
        }

        public void onConnectionFailed(String inputId) {
        }

        public void onDisconnected(String inputId) {
        }

        public void onTuned(Uri channelUri) {
        }

        public void onRecordingStopped(Uri recordedProgramUri) {
        }

        public void onError(int error) {
        }
    }

    private final Context mContext;
    private final String mTag;
    private final RecordingCallback mCallback;
    private final Handler mHandler;
    private boolean mIsRecording;
    private boolean mIsTuned;
    private boolean mIsPaused;
    private TvInteractiveAppView mTvIAppView;
    private String mRecordingId;

    public TvRecordingClient(
            Context context, String tag, RecordingCallback callback, Handler handler) {
        mContext = context;
        mTag = tag;
        mCallback = callback;
        mHandler = handler;
    }

    public void setTvInteractiveAppView(TvInteractiveAppView view, String recordingId) {
        if (view != null && recordingId == null) {
            throw new IllegalArgumentException(
                    "null recordingId is not allowed only when the view is not null");
        }
        if (view == null && recordingId != null) {
            throw new IllegalArgumentException(
                    "recordingId should be null when the view is null");
        }
        mTvIAppView = view;
        mRecordingId = recordingId;
    }

    public void tune(String inputId, Uri channelUri) {
        tune(inputId, channelUri, null);
    }

    public void tune(String inputId, final Uri channelUri, Bundle params) {
        if (inputId == null || inputId.isEmpty()) {
            throw new IllegalArgumentException("inputId cannot be empty");
        }
        if (mIsRecording) {
            throw new IllegalStateException("tune failed - recording already started");
        }
        mIsTuned = true;
        if (mCallback != null) {
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    mCallback.onTuned(channelUri);
                }
            };
            if (mHandler != null) {
                mHandler.post(r);
            } else {
                r.run();
            }
        }
    }

    public void release() {
        mIsRecording = false;
        mIsTuned = false;
        mIsPaused = false;
    }

    public void startRecording(Uri programUri) {
        startRecording(programUri, Bundle.EMPTY);
    }

    public void startRecording(Uri programUri, Bundle params) {
        if (!mIsTuned) {
            throw new IllegalStateException("startRecording failed - not yet tuned");
        }
        mIsRecording = true;
        mIsPaused = false;
    }

    public void stopRecording() {
        mIsRecording = false;
        mIsPaused = false;
    }

    public void pauseRecording() {
        pauseRecording(Bundle.EMPTY);
    }

    public void pauseRecording(Bundle params) {
        if (!mIsRecording) {
            throw new IllegalStateException("pauseRecording failed - recording not started");
        }
        mIsPaused = true;
    }

    public void resumeRecording() {
        resumeRecording(Bundle.EMPTY);
    }

    public void resumeRecording(Bundle params) {
        if (!mIsRecording || !mIsPaused) {
            throw new IllegalStateException("resumeRecording failed - recording not paused");
        }
        mIsPaused = false;
    }

    public void sendAppPrivateCommand(String action, Bundle data) {
        Objects.requireNonNull(action, "action cannot be null");
    }
}
