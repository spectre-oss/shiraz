// SPDX-License-Identifier: GPL-3.0-only
package android.telecom;

import android.os.Bundle;
import android.os.OutcomeReceiver;
import android.os.ParcelUuid;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Transactional controller for an active call created via {@link TelecomManager#addCall}. */
public final class CallControl {
    private final ParcelUuid mCallId;
    private final CallAttributes mAttributes;
    private final CallControlCallback mControlCallback;
    private final CallEventCallback mEventCallback;
    private final TelecomManager mOwner;

    private volatile boolean mTracked = true;
    private volatile int mState;
    private volatile boolean mMuted;
    private volatile int mVideoState;
    private volatile CallEndpoint mCurrentEndpoint;

    CallControl(
            CallAttributes attributes,
            CallControlCallback controlCallback,
            CallEventCallback eventCallback,
            TelecomManager owner) {
        this.mCallId = new ParcelUuid(UUID.randomUUID());
        this.mAttributes = attributes;
        this.mControlCallback = controlCallback;
        this.mEventCallback = eventCallback;
        this.mOwner = owner;
        this.mState =
                attributes.getDirection() == CallAttributes.DIRECTION_INCOMING
                        ? Call.STATE_RINGING
                        : Call.STATE_DIALING;
        this.mVideoState =
                attributes.getCallType() == CallAttributes.VIDEO_CALL
                        ? VideoProfile.STATE_BIDIRECTIONAL
                        : VideoProfile.STATE_AUDIO_ONLY;
        this.mCurrentEndpoint = new CallEndpoint("Earpiece", CallEndpoint.TYPE_EARPIECE);
    }

    public ParcelUuid getCallId() {
        return mCallId;
    }

    boolean isTracked() {
        return mTracked;
    }

    int getStateInternal() {
        return mState;
    }

    public void setActive(
            final Executor executor,
            final OutcomeReceiver<Void, CallException> callback) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(callback, "callback");
        if (!ensureTracked(executor, callback)) {
            return;
        }
        if (mControlCallback != null) {
            mControlCallback.onSetActive(
                    new Consumer<Boolean>() {
                        @Override
                        public void accept(Boolean ok) {
                            if (Boolean.TRUE.equals(ok)) {
                                mState = Call.STATE_ACTIVE;
                                completeSuccess(executor, callback);
                            } else {
                                completeError(
                                        executor,
                                        callback,
                                        new CallException(
                                                "onSetActive rejected",
                                                CallException.CODE_CALL_CANNOT_BE_SET_TO_ACTIVE));
                            }
                        }
                    });
        } else {
            mState = Call.STATE_ACTIVE;
            completeSuccess(executor, callback);
        }
    }

    public void answer(
            final int videoState,
            final Executor executor,
            final OutcomeReceiver<Void, CallException> callback) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(callback, "callback");
        if (videoState != CallAttributes.AUDIO_CALL
                && videoState != CallAttributes.VIDEO_CALL
                && !VideoProfile.isAudioOnly(videoState)
                && !VideoProfile.isVideo(videoState)) {
            throw new IllegalArgumentException("Invalid videoState: " + videoState);
        }
        if (!ensureTracked(executor, callback)) {
            return;
        }
        if (mControlCallback != null) {
            mControlCallback.onAnswer(
                    videoState,
                    new Consumer<Boolean>() {
                        @Override
                        public void accept(Boolean ok) {
                            if (Boolean.TRUE.equals(ok)) {
                                mState = Call.STATE_ACTIVE;
                                mVideoState = videoState;
                                completeSuccess(executor, callback);
                            } else {
                                completeError(
                                        executor,
                                        callback,
                                        new CallException(
                                                "onAnswer rejected",
                                                CallException.CODE_CALL_CANNOT_BE_SET_TO_ACTIVE));
                            }
                        }
                    });
        } else {
            mState = Call.STATE_ACTIVE;
            mVideoState = videoState;
            completeSuccess(executor, callback);
        }
    }

    public void setInactive(
            final Executor executor,
            final OutcomeReceiver<Void, CallException> callback) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(callback, "callback");
        if (!ensureTracked(executor, callback)) {
            return;
        }
        if ((mAttributes.getCallCapabilities() & CallAttributes.SUPPORTS_SET_INACTIVE) == 0) {
            completeError(
                    executor,
                    callback,
                    new CallException(
                            "Call does not support SUPPORTS_SET_INACTIVE",
                            CallException.CODE_CANNOT_HOLD_CURRENT_ACTIVE_CALL));
            return;
        }
        if (mControlCallback != null) {
            mControlCallback.onSetInactive(
                    new Consumer<Boolean>() {
                        @Override
                        public void accept(Boolean ok) {
                            if (Boolean.TRUE.equals(ok)) {
                                mState = Call.STATE_HOLDING;
                                completeSuccess(executor, callback);
                            } else {
                                completeError(
                                        executor,
                                        callback,
                                        new CallException(
                                                "onSetInactive rejected",
                                                CallException
                                                        .CODE_CANNOT_HOLD_CURRENT_ACTIVE_CALL));
                            }
                        }
                    });
        } else {
            mState = Call.STATE_HOLDING;
            completeSuccess(executor, callback);
        }
    }

    public void disconnect(
            final DisconnectCause disconnectCause,
            final Executor executor,
            final OutcomeReceiver<Void, CallException> callback) {
        Objects.requireNonNull(disconnectCause, "disconnectCause");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(callback, "callback");
        if (!ensureTracked(executor, callback)) {
            return;
        }
        if (mControlCallback != null) {
            mControlCallback.onDisconnect(
                    disconnectCause,
                    new Consumer<Boolean>() {
                        @Override
                        public void accept(Boolean ok) {
                            mState = Call.STATE_DISCONNECTED;
                            mTracked = false;
                            if (mOwner != null) {
                                mOwner.removeTrackedCall(CallControl.this);
                            }
                            if (Boolean.TRUE.equals(ok)) {
                                completeSuccess(executor, callback);
                            } else {
                                completeError(
                                        executor,
                                        callback,
                                        new CallException(
                                                "onDisconnect rejected",
                                                CallException.CODE_ERROR_UNKNOWN));
                            }
                        }
                    });
        } else {
            mState = Call.STATE_DISCONNECTED;
            mTracked = false;
            if (mOwner != null) {
                mOwner.removeTrackedCall(this);
            }
            completeSuccess(executor, callback);
        }
    }

    public void startCallStreaming(
            final Executor executor,
            final OutcomeReceiver<Void, CallException> callback) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(callback, "callback");
        if (!ensureTracked(executor, callback)) {
            return;
        }
        if (mControlCallback != null) {
            mControlCallback.onCallStreamingStarted(
                    new Consumer<Boolean>() {
                        @Override
                        public void accept(Boolean ok) {
                            if (Boolean.TRUE.equals(ok)) {
                                completeSuccess(executor, callback);
                            } else {
                                completeError(
                                        executor,
                                        callback,
                                        new CallException(
                                                "Streaming failed",
                                                CallException.CODE_ERROR_UNKNOWN));
                            }
                        }
                    });
        } else {
            completeSuccess(executor, callback);
        }
    }

    public void requestCallEndpointChange(
            final CallEndpoint callEndpoint,
            final Executor executor,
            final OutcomeReceiver<Void, CallException> callback) {
        Objects.requireNonNull(callEndpoint, "callEndpoint");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(callback, "callback");
        if (!ensureTracked(executor, callback)) {
            return;
        }
        mCurrentEndpoint = callEndpoint;
        if (mEventCallback != null) {
            mEventCallback.onCallEndpointChanged(callEndpoint);
        }
        completeSuccess(executor, callback);
    }

    public void requestMuteState(
            final boolean isMuted,
            final Executor executor,
            final OutcomeReceiver<Void, CallException> callback) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(callback, "callback");
        if (!ensureTracked(executor, callback)) {
            return;
        }
        mMuted = isMuted;
        if (mEventCallback != null) {
            mEventCallback.onMuteStateChanged(isMuted);
        }
        completeSuccess(executor, callback);
    }

    public void requestVideoState(
            final int videoState,
            final Executor executor,
            final OutcomeReceiver<Void, CallException> callback) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(callback, "callback");
        if (!ensureTracked(executor, callback)) {
            return;
        }
        mVideoState = videoState;
        if (mEventCallback != null) {
            mEventCallback.onVideoStateChanged(videoState);
        }
        completeSuccess(executor, callback);
    }

    public void sendEvent(String eventName, Bundle extras) {
        Objects.requireNonNull(eventName, "eventName");
        if (mTracked && mEventCallback != null) {
            mEventCallback.onEvent(eventName, extras == null ? Bundle.EMPTY : extras);
        }
    }

    private boolean ensureTracked(
            Executor executor, OutcomeReceiver<Void, CallException> callback) {
        if (!mTracked) {
            completeError(
                    executor,
                    callback,
                    new CallException(
                            "Call is no longer tracked",
                            CallException.CODE_CALL_IS_NOT_BEING_TRACKED));
            return false;
        }
        return true;
    }

    private static void completeSuccess(
            Executor executor, final OutcomeReceiver<Void, CallException> callback) {
        executor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        callback.onResult(null);
                    }
                });
    }

    private static void completeError(
            Executor executor,
            final OutcomeReceiver<Void, CallException> callback,
            final CallException error) {
        executor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        callback.onError(error);
                    }
                });
    }
}
