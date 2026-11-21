// SPDX-License-Identifier: GPL-3.0-only
package android.media.tv;

import android.content.AttributionSource;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Region;
import android.media.AudioPresentation;
import android.media.PlaybackParams;
import android.net.Uri;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class TvView extends ViewGroup {
    public interface OnUnhandledInputEventListener {
        boolean onUnhandledInputEvent(InputEvent event);
    }

    public abstract static class TimeShiftPositionCallback {
        public TimeShiftPositionCallback() {
        }

        public void onTimeShiftStartPositionChanged(String inputId, long timeMs) {
        }

        public void onTimeShiftCurrentPositionChanged(String inputId, long timeMs) {
        }
    }

    public abstract static class TvInputCallback {
        public TvInputCallback() {
        }

        public void onConnectionFailed(String inputId) {
        }

        public void onDisconnected(String inputId) {
        }

        public void onChannelRetuned(String inputId, Uri channelUri) {
        }

        public void onAudioPresentationsChanged(
                String inputId, List<AudioPresentation> audioPresentations) {
        }

        public void onAudioPresentationSelected(
                String inputId, int presentationId, int programId) {
        }

        public void onTracksChanged(String inputId, List<TvTrackInfo> tracks) {
        }

        public void onTrackSelected(String inputId, int type, String trackId) {
        }

        public void onVideoSizeChanged(String inputId, int width, int height) {
        }

        public void onVideoAvailable(String inputId) {
        }

        public void onVideoUnavailable(String inputId, int reason) {
        }

        public void onContentAllowed(String inputId) {
        }

        public void onContentBlocked(String inputId, TvContentRating rating) {
        }

        public void onTimeShiftStatusChanged(String inputId, int status) {
        }

        public void onAitInfoUpdated(String inputId, AitInfo aitInfo) {
        }

        public void onSignalStrengthUpdated(String inputId, int strength) {
        }

        public void onCueingMessageAvailability(String inputId, boolean available) {
        }

        public void onTimeShiftMode(String inputId, int mode) {
        }

        public void onAvailableSpeeds(String inputId, float[] speeds) {
        }

        public void onTuned(String inputId, Uri channelUri) {
        }

        public void onTvMessage(String inputId, int type, Bundle data) {
        }
    }

    private TvInputCallback mCallback;
    private TimeShiftPositionCallback mTimeShiftPositionCallback;
    private OnUnhandledInputEventListener mOnUnhandledInputEventListener;
    private AttributionSource mTvAppAttributionSource;
    private String mCurrentInputId;
    private Uri mCurrentChannelUri;
    private float mStreamVolume = 1.0f;
    private boolean mCaptionEnabled;
    private boolean mZOrderMediaOverlay;
    private boolean mZOrderOnTop;
    private final Map<Integer, List<TvTrackInfo>> mTracks =
            new HashMap<Integer, List<TvTrackInfo>>();
    private final Map<Integer, String> mSelectedTracks = new HashMap<Integer, String>();

    public TvView(Context context) {
        this(context, null, 0);
    }

    public TvView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TvView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setCallback(TvInputCallback callback) {
        mCallback = callback;
    }

    public void setZOrderMediaOverlay(boolean isMediaOverlay) {
        mZOrderMediaOverlay = isMediaOverlay;
    }

    public void setZOrderOnTop(boolean onTop) {
        mZOrderOnTop = onTop;
    }

    public void setStreamVolume(float volume) {
        if (volume < 0.0f || volume > 1.0f) {
            throw new IllegalArgumentException("volume should be between 0.0f and 1.0f");
        }
        mStreamVolume = volume;
    }

    public void overrideTvAppAttributionSource(AttributionSource tvAppAttributionSource) {
        mTvAppAttributionSource = Objects.requireNonNull(tvAppAttributionSource);
    }

    public void tune(String inputId, Uri channelUri) {
        tune(inputId, channelUri, null);
    }

    public void tune(String inputId, Uri channelUri, Bundle params) {
        if (inputId == null || inputId.isEmpty()) {
            throw new IllegalArgumentException("inputId cannot be empty");
        }
        mCurrentInputId = inputId;
        mCurrentChannelUri = channelUri;
        if (mCallback != null) {
            mCallback.onTuned(inputId, channelUri);
        }
    }

    public void reset() {
        mCurrentInputId = null;
        mCurrentChannelUri = null;
        mTracks.clear();
        mSelectedTracks.clear();
    }

    public void setCaptionEnabled(boolean enabled) {
        mCaptionEnabled = enabled;
    }

    public void selectAudioPresentation(int presentationId, int programId) {
    }

    public List<AudioPresentation> getAudioPresentations() {
        return new ArrayList<AudioPresentation>();
    }

    public void selectTrack(int type, String trackId) {
        if (type != TvTrackInfo.TYPE_AUDIO
                && type != TvTrackInfo.TYPE_VIDEO
                && type != TvTrackInfo.TYPE_SUBTITLE) {
            throw new IllegalArgumentException("Invalid type: " + type);
        }
        if (trackId == null) {
            mSelectedTracks.remove(type);
        } else {
            mSelectedTracks.put(type, trackId);
        }
    }

    public List<TvTrackInfo> getTracks(int type) {
        if (type != TvTrackInfo.TYPE_AUDIO
                && type != TvTrackInfo.TYPE_VIDEO
                && type != TvTrackInfo.TYPE_SUBTITLE) {
            throw new IllegalArgumentException("Invalid type: " + type);
        }
        List<TvTrackInfo> list = mTracks.get(type);
        return list != null
                ? Collections.unmodifiableList(new ArrayList<TvTrackInfo>(list))
                : null;
    }

    public String getSelectedTrack(int type) {
        if (type != TvTrackInfo.TYPE_AUDIO
                && type != TvTrackInfo.TYPE_VIDEO
                && type != TvTrackInfo.TYPE_SUBTITLE) {
            throw new IllegalArgumentException("Invalid type: " + type);
        }
        return mSelectedTracks.get(type);
    }

    public void setInteractiveAppNotificationEnabled(boolean enabled) {
    }

    public void timeShiftPlay(String inputId, Uri recordedProgramUri) {
        if (inputId == null || inputId.isEmpty()) {
            throw new IllegalArgumentException("inputId cannot be empty");
        }
        mCurrentInputId = inputId;
    }

    public void timeShiftPause() {
    }

    public void timeShiftResume() {
    }

    public void timeShiftSeekTo(long timeMs) {
    }

    public void timeShiftSetPlaybackParams(PlaybackParams params) {
        Objects.requireNonNull(params, "params cannot be null");
    }

    public void timeShiftSetMode(int mode) {
    }

    public void setTimeShiftPositionCallback(TimeShiftPositionCallback callback) {
        mTimeShiftPositionCallback = callback;
    }

    public void sendAppPrivateCommand(String action, Bundle data) {
        if (action == null || action.isEmpty()) {
            throw new IllegalArgumentException("action cannot be empty");
        }
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

    public void setOnUnhandledInputEventListener(OnUnhandledInputEventListener listener) {
        mOnUnhandledInputEventListener = listener;
    }

    public void setTvMessageEnabled(int type, boolean enabled) {
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return super.dispatchTouchEvent(event);
    }

    public boolean dispatchTrackballEvent(MotionEvent event) {
        return false;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        return super.dispatchGenericMotionEvent(event);
    }

    @Override
    public void dispatchWindowFocusChanged(boolean hasFocus) {
        super.dispatchWindowFocusChanged(hasFocus);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            if (child != null && child.getVisibility() != GONE) {
                child.layout(0, 0, right - left, bottom - top);
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    public boolean gatherTransparentRegion(Region region) {
        return true;
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
    }
}
