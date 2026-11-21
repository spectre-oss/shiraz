// SPDX-License-Identifier: GPL-3.0-only
package android.media.tv.interactive;

import android.content.Context;
import android.graphics.Rect;
import android.media.PlaybackParams;
import android.media.tv.TvRecordingInfo;
import android.media.tv.TvTrackInfo;
import android.media.tv.TvView;
import android.net.Uri;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

public class TvInteractiveAppView extends ViewGroup {
    public static final String BI_INTERACTIVE_APP_KEY_HTTP_ADDITIONAL_HEADERS =
            "http_additional_headers";
    public static final String BI_INTERACTIVE_APP_KEY_HTTP_USER_AGENT = "http_user_agent";
    public static final String BI_INTERACTIVE_APP_KEY_CERTIFICATE = "certificate";
    public static final String BI_INTERACTIVE_APP_KEY_PRIVATE_KEY = "private_key";
    public static final String BI_INTERACTIVE_APP_KEY_ALIAS = "alias";
    public static final String ERROR_KEY_METHOD_NAME = "method_name";

    public interface OnUnhandledInputEventListener {
        boolean onUnhandledInputEvent(InputEvent event);
    }

    public abstract static class TvInteractiveAppCallback {
        public TvInteractiveAppCallback() {
        }

        public void onPlaybackCommandRequest(
                String iAppServiceId, String cmdType, Bundle parameters) {
        }

        public void onTimeShiftCommandRequest(
                String iAppServiceId, String cmdType, Bundle parameters) {
        }

        public void onStateChanged(String iAppServiceId, int state, int err) {
        }

        public void onBiInteractiveAppCreated(
                String iAppServiceId, Uri biIAppUri, String biIAppId) {
        }

        public void onTeletextAppStateChanged(String iAppServiceId, int state) {
        }

        public void onSetVideoBounds(String iAppServiceId, Rect rect) {
        }

        public void onRequestCurrentVideoBounds(String iAppServiceId) {
        }

        public void onRequestCurrentChannelUri(String iAppServiceId) {
        }

        public void onRequestCurrentChannelLcn(String iAppServiceId) {
        }

        public void onRequestStreamVolume(String iAppServiceId) {
        }

        public void onRequestTrackInfoList(String iAppServiceId) {
        }

        public void onRequestCurrentTvInputId(String iAppServiceId) {
        }

        public void onRequestTimeShiftMode(String iAppServiceId) {
        }

        public void onRequestAvailableSpeeds(String iAppServiceId) {
        }

        public void onRequestStartRecording(
                String iAppServiceId, String requestId, Uri programUri) {
        }

        public void onRequestStopRecording(String iAppServiceId, String recordingId) {
        }

        public void onRequestScheduleRecording(
                String iAppServiceId,
                String requestId,
                String inputId,
                Uri channelUri,
                Uri programUri,
                Bundle params) {
        }

        public void onRequestScheduleRecording(
                String iAppServiceId,
                String requestId,
                String inputId,
                Uri channelUri,
                long startTime,
                long duration,
                int repeatDays,
                Bundle params) {
        }

        public void onRequestSigning(
                String iAppServiceId,
                String signingId,
                String algorithm,
                String alias,
                byte[] data) {
        }

        public void onSetTvRecordingInfo(
                String iAppServiceId, String recordingId, TvRecordingInfo recordingInfo) {
        }

        public void onRequestTvRecordingInfo(String iAppServiceId, String recordingId) {
        }

        public void onRequestTvRecordingInfoList(String iAppServiceId, int type) {
        }
    }

    private Executor mCallbackExecutor;
    private TvInteractiveAppCallback mCallback;
    private Executor mUnhandledInputExecutor;
    private OnUnhandledInputEventListener mOnUnhandledInputEventListener;
    private TvView mTvView;
    private String mIAppServiceId;
    private int mType;

    public TvInteractiveAppView(Context context) {
        this(context, null, 0);
    }

    public TvInteractiveAppView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TvInteractiveAppView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setCallback(Executor executor, TvInteractiveAppCallback callback) {
        Objects.requireNonNull(executor, "executor cannot be null");
        Objects.requireNonNull(callback, "callback cannot be null");
        mCallbackExecutor = executor;
        mCallback = callback;
    }

    public void clearCallback() {
        mCallbackExecutor = null;
        mCallback = null;
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
    }

    @Override
    public void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            if (child != null && child.getVisibility() != GONE) {
                child.layout(0, 0, right - left, bottom - top);
            }
        }
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
    }

    public void reset() {
        mIAppServiceId = null;
        mType = 0;
    }

    public boolean dispatchUnhandledInputEvent(InputEvent event) {
        if (mOnUnhandledInputEventListener != null) {
            if (mOnUnhandledInputEventListener.onUnhandledInputEvent(event)) {
                return true;
            }
        }
        return onUnhandledInputEvent(event);
    }

    public boolean onUnhandledInputEvent(InputEvent event) {
        return false;
    }

    public void setOnUnhandledInputEventListener(
            Executor executor, OnUnhandledInputEventListener listener) {
        Objects.requireNonNull(executor, "executor cannot be null");
        Objects.requireNonNull(listener, "listener cannot be null");
        mUnhandledInputExecutor = executor;
        mOnUnhandledInputEventListener = listener;
    }

    public OnUnhandledInputEventListener getOnUnhandledInputEventListener() {
        return mOnUnhandledInputEventListener;
    }

    public void clearOnUnhandledInputEventListener() {
        mUnhandledInputExecutor = null;
        mOnUnhandledInputEventListener = null;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return super.dispatchKeyEvent(event);
    }

    public void prepareInteractiveApp(String iAppServiceId, int type) {
        mIAppServiceId = iAppServiceId;
        mType = type;
    }

    public void startInteractiveApp() {
    }

    public void stopInteractiveApp() {
    }

    public void resetInteractiveApp() {
    }

    public void sendCurrentVideoBounds(Rect bounds) {
    }

    public void sendCurrentChannelUri(Uri channelUri) {
    }

    public void sendCurrentChannelLcn(int lcn) {
    }

    public void sendStreamVolume(float volume) {
    }

    public void sendTrackInfoList(List<TvTrackInfo> tracks) {
    }

    public void sendCurrentTvInputId(String inputId) {
    }

    public void sendTimeShiftMode(int mode) {
    }

    public void sendAvailableSpeeds(float[] speeds) {
    }

    public void sendTvRecordingInfo(TvRecordingInfo recordingInfo) {
    }

    public void sendTvRecordingInfoList(List<TvRecordingInfo> recordingInfoList) {
    }

    public void notifyRecordingStarted(String recordingId, String requestId) {
    }

    public void notifyRecordingStopped(String recordingId) {
    }

    public void sendSigningResult(String signingId, byte[] result) {
    }

    public void notifyError(String errMsg, Bundle params) {
    }

    public void notifyTimeShiftPlaybackParams(PlaybackParams params) {
    }

    public void notifyTimeShiftStatusChanged(String inputId, int status) {
    }

    public void notifyTimeShiftStartPositionChanged(String inputId, long timeMs) {
    }

    public void notifyTimeShiftCurrentPositionChanged(String inputId, long timeMs) {
    }

    public void notifyRecordingScheduled(String recordingId, String requestId) {
    }

    public void notifyTvMessage(int type, Bundle data) {
    }

    public void createBiInteractiveApp(Uri biIAppUri, Bundle params) {
    }

    public void destroyBiInteractiveApp(String biIAppId) {
    }

    public int setTvView(TvView tvView) {
        mTvView = tvView;
        return TvInteractiveAppManager.ERROR_NONE;
    }

    public void setTeletextAppEnabled(boolean enable) {
    }
}
