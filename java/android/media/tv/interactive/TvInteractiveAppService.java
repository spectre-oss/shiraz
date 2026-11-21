// SPDX-License-Identifier: GPL-3.0-only
package android.media.tv.interactive;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.media.PlaybackParams;
import android.media.tv.AdBuffer;
import android.media.tv.AdRequest;
import android.media.tv.AdResponse;
import android.media.tv.BroadcastInfoRequest;
import android.media.tv.BroadcastInfoResponse;
import android.media.tv.TvContentRating;
import android.media.tv.TvRecordingInfo;
import android.media.tv.TvTrackInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import java.util.List;

public abstract class TvInteractiveAppService extends Service {
    public static final String SERVICE_INTERFACE =
            "android.media.tv.interactive.TvInteractiveAppService";
    public static final String SERVICE_META_DATA = "android.media.tv.interactive.app";

    public static final String PLAYBACK_COMMAND_TYPE_TUNE = "tune";
    public static final String PLAYBACK_COMMAND_TYPE_TUNE_NEXT = "tune_next";
    public static final String PLAYBACK_COMMAND_TYPE_TUNE_PREV = "tune_previous";
    public static final String PLAYBACK_COMMAND_TYPE_STOP = "stop";
    public static final String PLAYBACK_COMMAND_TYPE_SET_STREAM_VOLUME = "set_stream_volume";
    public static final String PLAYBACK_COMMAND_TYPE_SELECT_TRACK = "select_track";

    public static final String COMMAND_PARAMETER_KEY_CHANNEL_URI = "command_channel_uri";
    public static final String COMMAND_PARAMETER_KEY_PROGRAM_URI = "command_program_uri";
    public static final String COMMAND_PARAMETER_KEY_INPUT_ID = "command_input_id";
    public static final String COMMAND_PARAMETER_KEY_CHANGE_CHANNEL_QUIETLY =
            "command_change_channel_quietly";
    public static final String COMMAND_PARAMETER_KEY_VOLUME = "command_volume";
    public static final String COMMAND_PARAMETER_KEY_TRACK_TYPE = "command_track_type";
    public static final String COMMAND_PARAMETER_KEY_TRACK_ID = "command_track_id";
    public static final String COMMAND_PARAMETER_KEY_STOP_MODE = "command_stop_mode";
    public static final int COMMAND_PARAMETER_VALUE_STOP_MODE_BLANK = 1;
    public static final int COMMAND_PARAMETER_VALUE_STOP_MODE_FREEZE = 2;

    public static final String TIME_SHIFT_COMMAND_TYPE_PLAY = "play";
    public static final String TIME_SHIFT_COMMAND_TYPE_PAUSE = "pause";
    public static final String TIME_SHIFT_COMMAND_TYPE_RESUME = "resume";
    public static final String TIME_SHIFT_COMMAND_TYPE_SEEK_TO = "seek_to";
    public static final String TIME_SHIFT_COMMAND_TYPE_SET_PLAYBACK_PARAMS =
            "set_playback_params";
    public static final String TIME_SHIFT_COMMAND_TYPE_SET_MODE = "set_mode";

    public static final String COMMAND_PARAMETER_KEY_TIME_POSITION = "command_time_position";
    public static final String COMMAND_PARAMETER_KEY_PLAYBACK_PARAMS = "command_playback_params";
    public static final String COMMAND_PARAMETER_KEY_TIME_SHIFT_MODE = "command_time_shift_mode";

    public abstract static class Session implements KeyEvent.Callback {
        private final Context mContext;
        private boolean mMediaViewEnabled;

        public Session(Context context) {
            mContext = context;
        }

        public void setMediaViewEnabled(boolean enable) {
            mMediaViewEnabled = enable;
        }

        public boolean isMediaViewEnabled() {
            return mMediaViewEnabled;
        }

        public void onStartInteractiveApp() {
        }

        public void onStopInteractiveApp() {
        }

        public void onResetInteractiveApp() {
        }

        public void onCreateBiInteractiveAppRequest(Uri biIAppUri, Bundle params) {
        }

        public void onDestroyBiInteractiveAppRequest(String biIAppId) {
        }

        public void onSetTeletextAppEnabled(boolean enable) {
        }

        public void onCurrentVideoBounds(Rect bounds) {
        }

        public void onCurrentChannelUri(Uri channelUri) {
        }

        public void onCurrentChannelLcn(int lcn) {
        }

        public void onStreamVolume(float volume) {
        }

        public void onTrackInfoList(List<TvTrackInfo> tracks) {
        }

        public void onCurrentTvInputId(String inputId) {
        }

        public void onTimeShiftMode(int mode) {
        }

        public void onAvailableSpeeds(float[] speeds) {
        }

        public void onTvRecordingInfo(TvRecordingInfo recordingInfo) {
        }

        public void onTvRecordingInfoList(List<TvRecordingInfo> recordingInfoList) {
        }

        public void onRecordingStarted(String recordingId, String requestId) {
        }

        public void onRecordingStopped(String recordingId) {
        }

        public void onRecordingConnectionFailed(String recordingId, String inputId) {
        }

        public void onRecordingDisconnected(String recordingId, String inputId) {
        }

        public void onRecordingTuned(String recordingId, Uri channelUri) {
        }

        public void onRecordingError(String recordingId, int err) {
        }

        public void onRecordingScheduled(String recordingId, String requestId) {
        }

        public void onSigningResult(String signingId, byte[] result) {
        }

        public void onError(String errMsg, Bundle params) {
        }

        public void onTimeShiftPlaybackParams(PlaybackParams params) {
        }

        public void onTimeShiftStatusChanged(String inputId, int status) {
        }

        public void onTimeShiftStartPositionChanged(String inputId, long timeMs) {
        }

        public void onTimeShiftCurrentPositionChanged(String inputId, long timeMs) {
        }

        public abstract boolean onSetSurface(Surface surface);

        public void onSurfaceChanged(int format, int width, int height) {
        }

        public void onMediaViewSizeChanged(int width, int height) {
        }

        public View onCreateMediaView() {
            return null;
        }

        public abstract void onRelease();

        public void onTuned(Uri channelUri) {
        }

        public void onTrackSelected(int type, String trackId) {
        }

        public void onTracksChanged(List<TvTrackInfo> tracks) {
        }

        public void onVideoAvailable() {
        }

        public void onVideoUnavailable(int reason) {
        }

        public void onContentAllowed() {
        }

        public void onContentBlocked(TvContentRating rating) {
        }

        public void onSignalStrength(int strength) {
        }

        public void onBroadcastInfoResponse(BroadcastInfoResponse response) {
        }

        public void onAdResponse(AdResponse response) {
        }

        public void onAdBufferConsumed(AdBuffer buffer) {
        }

        public void onTvMessage(int type, Bundle data) {
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

        public void layoutSurface(int left, int top, int right, int bottom) {
        }

        public void requestBroadcastInfo(BroadcastInfoRequest request) {
        }

        public void removeBroadcastInfo(int requestId) {
        }

        public void sendPlaybackCommandRequest(String cmdType, Bundle parameters) {
        }

        public void sendTimeShiftCommandRequest(String cmdType, Bundle parameters) {
        }

        public void setVideoBounds(Rect rect) {
        }

        public void requestCurrentVideoBounds() {
        }

        public void requestCurrentChannelUri() {
        }

        public void requestCurrentChannelLcn() {
        }

        public void requestStreamVolume() {
        }

        public void requestTrackInfoList() {
        }

        public void requestCurrentTvInputId() {
        }

        public void requestTimeShiftMode() {
        }

        public void requestAvailableSpeeds() {
        }

        public void requestStartRecording(String requestId, Uri programUri) {
        }

        public void requestStopRecording(String recordingId) {
        }

        public void requestScheduleRecording(
                String requestId,
                String inputId,
                Uri channelUri,
                Uri programUri,
                Bundle params) {
        }

        public void requestScheduleRecording(
                String requestId,
                String inputId,
                Uri channelUri,
                long startTime,
                long duration,
                int repeatDays,
                Bundle params) {
        }

        public void setTvRecordingInfo(String recordingId, TvRecordingInfo recordingInfo) {
        }

        public void requestTvRecordingInfo(String recordingId) {
        }

        public void requestTvRecordingInfoList(int type) {
        }

        public void requestSigning(
                String signingId, String algorithm, String alias, byte[] data) {
        }

        public void requestAd(AdRequest request) {
        }

        public void notifySessionStateChanged(int state, int err) {
        }

        public final void notifyBiInteractiveAppCreated(Uri biIAppUri, String biIAppId) {
        }

        public final void notifyTeletextAppStateChanged(int state) {
        }

        public void notifyAdBufferReady(AdBuffer buffer) {
        }
    }

    private final IBinder mBinder = new Binder();

    public TvInteractiveAppService() {
    }

    @Override
    public final IBinder onBind(Intent intent) {
        if (intent != null && SERVICE_INTERFACE.equals(intent.getAction())) {
            return mBinder;
        }
        return null;
    }

    public void onRegisterAppLinkInfo(AppLinkInfo appLinkInfo) {
    }

    public void onUnregisterAppLinkInfo(AppLinkInfo appLinkInfo) {
    }

    public void onAppLinkCommand(Bundle command) {
    }

    public abstract Session onCreateSession(String iAppServiceId, int type);

    public final void notifyStateChanged(int type, int state, int err) {
    }
}
