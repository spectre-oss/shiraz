// SPDX-License-Identifier: GPL-3.0-only
package android.media.tv;

import android.app.Service;
import android.content.AttributionSource;
import android.content.Context;
import android.content.Intent;
import android.media.AudioPresentation;
import android.media.PlaybackParams;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import java.util.List;

public abstract class TvInputService extends Service {
    public static final String SERVICE_INTERFACE = "android.media.tv.TvInputService";
    public static final String SERVICE_META_DATA = "android.media.tv.input";

    public static final int PRIORITY_HINT_USE_CASE_TYPE_BACKGROUND = 100;
    public static final int PRIORITY_HINT_USE_CASE_TYPE_SCAN = 200;
    public static final int PRIORITY_HINT_USE_CASE_TYPE_PLAYBACK = 300;
    public static final int PRIORITY_HINT_USE_CASE_TYPE_LIVE = 400;
    public static final int PRIORITY_HINT_USE_CASE_TYPE_RECORD = 500;

    public abstract static class Session implements KeyEvent.Callback {
        private final Context mContext;
        private boolean mOverlayViewEnabled;

        public Session(Context context) {
            mContext = context;
        }

        public void setOverlayViewEnabled(boolean enable) {
            mOverlayViewEnabled = enable;
        }

        public void notifyChannelRetuned(Uri channelUri) {
        }

        public void notifyTuned(Uri channelUri) {
        }

        public void notifyTracksChanged(List<TvTrackInfo> tracks) {
        }

        public void notifyTrackSelected(int type, String trackId) {
        }

        public void notifyVideoAvailable() {
        }

        public void notifyVideoUnavailable(int reason) {
        }

        public void notifyAudioPresentationChanged(List<AudioPresentation> audioPresentations) {
        }

        public void notifyAudioPresentationSelected(int presentationId, int programId) {
        }

        public void notifyContentAllowed() {
        }

        public void notifyContentBlocked(TvContentRating rating) {
        }

        public void notifyTimeShiftStatusChanged(int status) {
        }

        public void notifyBroadcastInfoResponse(BroadcastInfoResponse response) {
        }

        public void notifyAdResponse(AdResponse response) {
        }

        public void notifyAdBufferConsumed(AdBuffer buffer) {
        }

        public void notifyTvMessage(int type, Bundle data) {
        }

        public void notifyAitInfoUpdated(AitInfo aitInfo) {
        }

        public void notifyTimeShiftMode(int mode) {
        }

        public void notifyAvailableSpeeds(float[] speeds) {
        }

        public void notifySignalStrength(int strength) {
        }

        public void notifyCueingMessageAvailability(boolean available) {
        }

        public void layoutSurface(int left, int top, int right, int bottom) {
        }

        public abstract void onRelease();

        public abstract boolean onSetSurface(Surface surface);

        public void onSurfaceChanged(int format, int width, int height) {
        }

        public void onOverlayViewSizeChanged(int width, int height) {
        }

        public abstract void onSetStreamVolume(float volume);

        public void onRequestBroadcastInfo(BroadcastInfoRequest request) {
        }

        public void onRemoveBroadcastInfo(int requestId) {
        }

        public void onRequestAd(AdRequest request) {
        }

        public void onAdBufferReady(AdBuffer buffer) {
        }

        public abstract boolean onTune(Uri channelUri);

        public boolean onTune(Uri channelUri, Bundle params) {
            return onTune(channelUri);
        }

        public abstract void onSetCaptionEnabled(boolean enabled);

        public void onUnblockContent(TvContentRating unblockedRating) {
        }

        public boolean onSelectTrack(int type, String trackId) {
            return false;
        }

        public void onSetInteractiveAppNotificationEnabled(boolean enabled) {
        }

        public boolean onSelectAudioPresentation(int presentationId, int programId) {
            return false;
        }

        public void onAppPrivateCommand(String action, Bundle data) {
        }

        public View onCreateOverlayView() {
            return null;
        }

        public void onSetTvMessageEnabled(int type, boolean enabled) {
        }

        public void onTvMessage(int type, Bundle data) {
        }

        public void onTimeShiftPlay(Uri recordedProgramUri) {
        }

        public void onTimeShiftPause() {
        }

        public void onTimeShiftResume() {
        }

        public void onTimeShiftSeekTo(long timeMs) {
        }

        public void onTimeShiftSetPlaybackParams(PlaybackParams params) {
        }

        public void onTimeShiftSetMode(int mode) {
        }

        public long onTimeShiftGetStartPosition() {
            return TvInputManager.TIME_SHIFT_INVALID_TIME;
        }

        public long onTimeShiftGetCurrentPosition() {
            return TvInputManager.TIME_SHIFT_INVALID_TIME;
        }

        @Override
        public boolean onKeyDown(int keyCode, KeyEvent event) {
            return false;
        }

        @Override
        public boolean onKeyLongPress(int keyCode, KeyEvent event) {
            return false;
        }

        @Override
        public boolean onKeyMultiple(int keyCode, int count, KeyEvent event) {
            return false;
        }

        @Override
        public boolean onKeyUp(int keyCode, KeyEvent event) {
            return false;
        }

        public boolean onTouchEvent(MotionEvent event) {
            return false;
        }

        public boolean onTrackballEvent(MotionEvent event) {
            return false;
        }

        public boolean onGenericMotionEvent(MotionEvent event) {
            return false;
        }
    }

    public abstract static class HardwareSession extends Session {
        public HardwareSession(Context context) {
            super(context);
        }

        public abstract String getHardwareInputId();

        @Override
        public final boolean onSetSurface(Surface surface) {
            return false;
        }

        public void onHardwareVideoAvailable() {
        }

        public void onHardwareVideoUnavailable(int reason) {
        }
    }

    public abstract static class RecordingSession {
        private final Context mContext;

        public RecordingSession(Context context) {
            mContext = context;
        }

        public void notifyTuned(Uri channelUri) {
        }

        public void notifyRecordingStopped(Uri recordedProgramUri) {
        }

        public void notifyError(int error) {
        }

        public abstract void onTune(Uri channelUri);

        public void onTune(Uri channelUri, Bundle params) {
            onTune(channelUri);
        }

        public abstract void onStartRecording(Uri programUri);

        public void onStartRecording(Uri programUri, Bundle params) {
            onStartRecording(programUri);
        }

        public abstract void onStopRecording();

        public void onPauseRecording(Bundle params) {
        }

        public void onResumeRecording(Bundle params) {
        }

        public abstract void onRelease();

        public void onAppPrivateCommand(String action, Bundle data) {
        }
    }

    private final IBinder mBinder = new Binder();

    public TvInputService() {
    }

    @Override
    public final IBinder onBind(Intent intent) {
        return mBinder;
    }

    public abstract Session onCreateSession(String inputId);

    public RecordingSession onCreateRecordingSession(String inputId) {
        return null;
    }

    public Session onCreateSession(String inputId, String sessionId) {
        return onCreateSession(inputId);
    }

    public Session onCreateSession(
            String inputId, String sessionId, AttributionSource tvAppAttributionSource) {
        return onCreateSession(inputId, sessionId);
    }

    public RecordingSession onCreateRecordingSession(String inputId, String sessionId) {
        return onCreateRecordingSession(inputId);
    }
}
